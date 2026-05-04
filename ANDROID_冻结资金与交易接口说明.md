# Android 端：冻结资金 / 冻结持仓与交易接口说明

> **环境与 Base URL**：见 **`ANDROID_环境与BaseURL对齐说明.md`**。

面向客户端开发与产品设计，说明**资金与股份如何冻结、何时解冻**，以及**调哪些接口、界面怎么设计**。

---

## 1. 业务概念（给产品 / 开发对齐）

### 1.1 买入委托

- 下单成功后，系统会把**委托价 × 委托数量**对应的资金从「可用」划到「冻结」：  
  **可用资金减少、冻结资金增加**，这笔钱专门用于该笔委托可能的成交与风控占用。
- **撤单**：未成交部分对应的冻结资金**一次性退回可用**（由服务端按当前委托剩余冻结处理）。
- **成交**（生产一般由交易所回报驱动；本演示环境可由柜台「模拟成交」入账）：  
  按成交数量解冻「限价对应的冻结块」，成交价低于限价时，**多冻部分退回可用**；实际成交金额用于增加持仓成本等。

### 1.2 卖出委托

- 下单成功后，会把**委托数量**对应的股份从「可卖」划到「冻结」：  
  **可卖减少、冻结股数增加**。
- **撤单**：未成交部分的冻结股份**退回可卖**。
- **成交**：从冻结与总持仓中扣减卖出数量，**成交金额进入可用资金**（演示逻辑，不含细项费用）。

### 1.3 与界面展示的关系

- 资金账户接口会返回 **`availableBalance`（可用）**、**`frozenBalance`（冻结）**、以及 **`currentBalance`（当前余额）** 等，建议在「资金」页同时展示**可用 / 冻结**。
- **口径约定**：`currentBalance` 与 **`availableBalance + frozenBalance`** 表示同一笔资金的总账（总现金 = 可用 + 冻结）；下单时从可用划到冻结，**总额不变**；成交后总额随实际支出与费用减少。若曾使用旧版后端（下单/撤单未写回 `current_balance`），可能出现「可用与当前余额不一致」；请升级后端并执行仓库内 **`sql/patch_acct_fund_account_repair_current_balance.sql`** 一次性纠偏。
- 委托列表每条可展示：委托价、数量、已成、剩余、状态；**是否可撤**以后端返回的 **`canCancel`** 为准。

---

## 2. Base URL 与通用约定

- **App 接口 Base**：`http://<host>:28480/api/app`（下文路径均相对该前缀；端口以服务端 `SERVER_PORT` 为准）。
- 统一响应：`{ "code": 0, "message": "success", "data": { ... } }`；`code != 0` 表示失败，**优先用 `message` 提示用户**。
- JSON 字段为 **camelCase**（如 `fundAccountNo`、`requestSeqNo`）。列表里价格同时提供 **`orderPrice`** 与别名 **`price`**，数量有 **`orderQty`** 与 **`quantity`**，便于 Gson 直接映射。

---

## 3. 接口清单（与冻结相关）

| 能力 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 资金账户（含可用/冻结） | GET | `/trade/fund-accounts?userId=` | 展示下单前可用、冻结 |
| 风控预校验 | POST | `/trade/risk/precheck` | 可选；校验后再下单 |
| 下单 | POST | `/trade/orders` | **触发冻结**（买：冻资；卖：冻券） |
| 委托列表 | GET | `/trade/orders?userId=` | 含 **`orderListBuckets`**、**`canCancel`**；可选 **`orderListCategory`** 按 Tab 筛数据 |
| 撤单 | POST | `/trade/orders/{orderNo}/cancel` | **释放剩余冻结**（未成交部分） |
| 资产总览 | GET | `/assets/overview?userId=` | 总资产、可用现金、持仓市值、浮动盈亏等 |
| 持仓明细 | GET | `/assets/positions?userId=` | 按证券的持仓条数、多类价格、市值（资产页「查看持仓」） |

> **模拟成交**仅柜台操作员接口（`/api/operator/...`），App 端一般不调用；生产成交由撮合/回报系统对接。

---

## 4. 接口要点与请求示例

### 4.1 资金账户 `GET /trade/fund-accounts?userId={id}`

- 用途：下单页下拉选择资金户，并展示 **可用 / 冻结**。
- `data` 为数组，元素含：`fundAccountNo`、`availableBalance`、`frozenBalance`、`status` 等。

### 4.2 下单 `POST /trade/orders`

**Body 示例：**

```json
{
  "userId": 10001,
  "fundAccountNo": "FA20260430010001",
  "marketCode": "SH",
  "securityCode": "600000",
  "tradeDirection": "BUY",
  "price": 10.25,
  "quantity": 100,
  "requestSeqNo": "APP_ORDER_20260201_001"
}
```

- **`requestSeqNo`**：客户端生成的**幂等键**，须**全局唯一**；建议长度 **不超过 64 字符**（数据库上限已放宽，仍建议短且唯一，如前缀 + 时间戳 + 随机串）。
- 成功 `data` 中含：`orderNo`、`orderStatus`（一般为 `REPORTED`）、**`frozenCash`**（买）或 **`frozenQty`**（卖）等，可用于「下单成功」页简要提示：*已冻结资金/股份用于本笔委托*。

### 4.3 委托列表 `GET /trade/orders?userId={id}`

**常用 Query：**

- `userId`（必填）
- `fundAccountNo`（可选）
- `orderStatus`（可选，精确匹配单笔状态，如 `REPORTED`）
- **`orderListCategory`（可选）**：与「未完成 / 已完成 / 已撤单」三 Tab 对齐，由服务端筛数据：
  - **`ONGOING`**：`INIT`、`REPORTED`、`PART_FILLED`（进行中）
  - **`COMPLETED`**：**有成交**的终结态——`FILLED`（全成）以及 **`PART_CANCELED` 且 `filled_qty > 0`**（部成后撤剩余）
  - **`CANCELED`**：**含撤单**的终结态——`CANCELED`（全撤）与 **`PART_CANCELED`**（部撤）
  - 传 **`FILLED`** 时与 **`COMPLETED`** 同义（兼容旧参数名）
- `page`、`pageSize`

**说明：** 部撤 **`PART_CANCELED`** 既属于「有成交」又属于「有撤单」，因此会**同时**出现在 **`COMPLETED`** 与 **`CANCELED`** 两种筛选结果中；全成单只在 **`COMPLETED`**，全撤无成交单只在 **`CANCELED`**。

列表项重点字段：

- `orderNo`、`orderPrice` / `price`、`orderQty` / `quantity`、`filledQty`、`remainQty`、`orderStatus`
- **`orderListBuckets`**（字符串数组）：该笔委托应出现在哪些 Tab。取值来自 `COMPLETED` / `CANCELED` / `ONGOING`（与查询参数同语义）。例如部撤且有成：`["COMPLETED","CANCELED"]`；全成：`["COMPLETED"]`；全撤：`["CANCELED"]`；进行中：`["ONGOING"]`。**本地分 Tab 时请按桶判断**（如 `buckets.contains("COMPLETED")`），不要仅用 `orderStatus == "FILLED"` 或 `== "CANCELED"` 单键判断。
- **`canCancel`**：`true` 时可展示撤单入口（仍可能因并发被服务端拒绝，以接口返回为准）。
- 已终结委托（全成、全撤、部撤）的 **`remainQty` 为 0**（不再展示「剩余可撤数量」语义上的未平量）。

### 4.4 撤单 `POST /trade/orders/{orderNo}/cancel`

**Body 示例：**

```json
{
  "userId": 10001,
  "requestSeqNo": "APP_CANCEL_20260201_001",
  "cancelReason": "用户撤单"
}
```

- 撤单成功后，服务端会**释放该笔委托剩余冻结**（资金或股份），资金账户 / 持仓刷新后即可看到可用、冻结变化。
- **`requestSeqNo`** 同样须唯一，建议命名空间与下单区分（如 `APP_CANCEL_` 前缀）。

### 4.5 资产总览 `GET /assets/overview?userId={id}`

- 返回 `data`：`availableCash`（各资金户可用之和）、`totalAsset`（现金「可用+冻结」合计 + 持仓市值）、`marketValue`、`profitLoss` 等。
- 与 **`/trade/fund-accounts`** 配合展示资金卡片；刷新资产页时建议两个接口一起拉。

### 4.6 持仓明细 `GET /assets/positions?userId={id}`

- **用途**：资产页在「持仓汇总」下方增加 **「查看持仓明细」** 按钮，点击后请求本接口，在弹窗或新页面展示列表。
- **成功时 `data` 为数组**，每条示例字段：
  - **`market` / `marketCode`**：如 `SH` / `1`（沪）、`SZ` / `0`（深）
  - **`securityCode`、`securityName`**：证券代码与名称
  - **`totalQty`**：总持仓股数；**`availableQty`**：可卖；**`frozenQty`**：委托冻结
  - **`costPrice`**：成本价（加权）；**`lastPrice`**：持仓上记录的最近价/成交价口径
  - **`quotePrice`**：行情表最新价（无行情时为 `null`）
  - **`marketValue`**：持仓市值；**`positionStatus`**：状态

---

## 5. Android 端设计建议

### 5.1 下单页

1. 进入页先请求 **`/trade/fund-accounts`**，展示每个户的 **可用 / 冻结**（可用不足时禁用或提示「可用不足」）。
2. 可选：先 **`/trade/risk/precheck`**，通过后再允许提交 **`/trade/orders`**。
3. 提交成功后跳转委托详情或列表；Toast/文案可提示：**已按委托冻结资金/股份**（引用返回的 `frozenCash` / `frozenQty` 更专业）。

### 5.2 委托列表页（推荐替代「手输 orderNo 撤单」）

1. **推荐**：三个 Tab 分别请求 **`GET /trade/orders?userId=...&orderListCategory=ONGOING|COMPLETED|CANCELED`**（`FILLED` 与 `COMPLETED` 等价）；或一次拉全量后按每条 **`orderListBuckets`** 分桶：未完成只显示含 `ONGOING` 的；已完成显示含 **`COMPLETED`** 的；已撤单显示含 **`CANCELED`** 的（部撤会同时出现在后两个 Tab）。
2. 仅当 **`canCancel === true`** 时显示「撤单」按钮；点击后确认弹窗，再调 **`POST .../cancel`**。
3. 撤单成功后 **重新拉列表 + 刷新资金账户**，保证冻结数字与服务器一致。

### 5.3 资金页 / 资产总览

1. 定期或从下单/撤单返回后刷新 **`/trade/fund-accounts`** 与 **`/assets/overview`**，保证汇总与卡片一致。
2. 用文案说明：**冻结资金为委托占用，撤单或成交后按规则释放**（避免客诉）。
3. **持仓明细按钮**：在占位文案处增加按钮「查看持仓明细」→ 调 **`GET /assets/positions?userId=`**，用 `RecyclerView` / `LazyColumn` 展示列表；主行展示 **名称 + 代码 + 总股数**；副行展示 **成本价、行情价（`quotePrice`）、市值**；无 `quotePrice` 时可仅用 `lastPrice`。

### 5.4 数据模型（Gson）

- 列表项价格字段请映射 **`orderPrice`** 或 **`price`**（后端两者等价）；数量 **`orderQty`** / **`quantity`**。
- **`orderListBuckets`**：JSON 数组，Gson 可映射为 `List<String>` 或 `String[]`，用于 Tab 展示逻辑。
- `BigDecimal` 建议用 `String` 接 JSON 再转，避免精度问题。

### 5.5 错误与幂等

- 重复 **`requestSeqNo`** 下单或撤单：服务端可能返回幂等结构（如含 `idempotent: true`），客户端应视为成功或已受理，避免重复扣款/重复撤单 UI。
- `code != 0`：展示 `message`；常见如登录态失效、未开户、可用不足、当前状态不可撤单等。

---

## 6. 与柜台演示环境的差异说明

- 本仓库**柜台端**提供「模拟成交」用于演示成交后冻结释放路径；**App 仅通过真实回报对接时**，应对接券商/柜台提供的成交推送或查询接口，而不是调用操作员模拟接口。
- 冻结/解冻的**会计口径**以服务端为准；客户端只做展示与重试策略，不做本地加减冻结以免与账务不一致。

---

## 7. 文档版本

- 与后端路径一致：`/api/app/trade/...`；操作员接口为 `/api/operator/trade/...`。
- 若接口字段有变更，以 `TradeController`、`AppApiController` 源码为准。
