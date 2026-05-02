# Android 端：冻结资金 / 冻结持仓与交易接口说明

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

- 资金账户接口会返回 **`availableBalance`（可用）**、**`frozenBalance`（冻结）**、以及 **`currentBalance`** 等，建议在「资金」页同时展示**可用 / 冻结**，避免用户误以为下单后钱「消失」。
- 委托列表每条可展示：委托价、数量、已成、剩余、状态；**是否可撤**以后端返回的 **`canCancel`** 为准。

---

## 2. Base URL 与通用约定

- **App 接口 Base**：`http://<host>:8080/api/app`（下文路径均相对该前缀）。
- 统一响应：`{ "code": 0, "message": "success", "data": { ... } }`；`code != 0` 表示失败，**优先用 `message` 提示用户**。
- JSON 字段为 **camelCase**（如 `fundAccountNo`、`requestSeqNo`）。列表里价格同时提供 **`orderPrice`** 与别名 **`price`**，数量有 **`orderQty`** 与 **`quantity`**，便于 Gson 直接映射。

---

## 3. 接口清单（与冻结相关）

| 能力 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 资金账户（含可用/冻结） | GET | `/trade/fund-accounts?userId=` | 展示下单前可用、冻结 |
| 风控预校验 | POST | `/trade/risk/precheck` | 可选；校验后再下单 |
| 下单 | POST | `/trade/orders` | **触发冻结**（买：冻资；卖：冻券） |
| 委托列表 | GET | `/trade/orders?userId=` | 含 **`canCancel`**、成交量、状态等 |
| 撤单 | POST | `/trade/orders/{orderNo}/cancel` | **释放剩余冻结**（未成交部分） |

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
- `orderStatus`（可选，如 `REPORTED`）
- `page`、`pageSize`

列表项重点字段：

- `orderNo`、`orderPrice` / `price`、`orderQty` / `quantity`、`filledQty`、`remainQty`、`orderStatus`
- **`canCancel`**：`true` 时可展示撤单入口（仍可能因并发被服务端拒绝，以接口返回为准）。

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

---

## 5. Android 端设计建议

### 5.1 下单页

1. 进入页先请求 **`/trade/fund-accounts`**，展示每个户的 **可用 / 冻结**（可用不足时禁用或提示「可用不足」）。
2. 可选：先 **`/trade/risk/precheck`**，通过后再允许提交 **`/trade/orders`**。
3. 提交成功后跳转委托详情或列表；Toast/文案可提示：**已按委托冻结资金/股份**（引用返回的 `frozenCash` / `frozenQty` 更专业）。

### 5.2 委托列表页（推荐替代「手输 orderNo 撤单」）

1. 使用 **`GET /trade/orders`** 拉列表，按状态筛选 Tab（进行中 / 已撤单 等可由客户端本地分桶 + 后端状态字段组合实现）。
2. 仅当 **`canCancel === true`** 时显示「撤单」按钮；点击后确认弹窗，再调 **`POST .../cancel`**。
3. 撤单成功后 **重新拉列表 + 刷新资金账户**，保证冻结数字与服务器一致。

### 5.3 资金页 / 资产总览

1. 定期或从下单/撤单返回后刷新 **`/trade/fund-accounts`**。
2. 用文案说明：**冻结资金为委托占用，撤单或成交后按规则释放**（避免客诉）。

### 5.4 数据模型（Gson）

- 列表项价格字段请映射 **`orderPrice`** 或 **`price`**（后端两者等价）；数量 **`orderQty`** / **`quantity`**。
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
