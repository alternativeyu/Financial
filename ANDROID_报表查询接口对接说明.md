# Android 端：报表查询接口对接说明

> **环境与 Base URL**：见 **`ANDROID_环境与BaseURL对齐说明.md`**。  
> **委托 / 资金 / 持仓**：见 **`ANDROID_冻结资金与交易接口说明.md`**、**`ANDROID_委托撤单多资金账户对接文档.md`**。

本文说明 **App 用户端** 在「客户资料、资产流水、成交、风控、通知」等报表类能力上应调用的接口；服务端通过 **统一查询服务** 封装数据库访问，App **仅传 `userId`**，**不传**客户代码、资金账号等越权定位参数（由后端根据开户绑定解析为本人客户）。

---

## 1. Base URL 与路径前缀

- App 接口根路径仍为 **`/api/app`**（与登录、开户、交易、行情一致）。
- 报表类接口子路径为 **`/api/app/report/**`**。
- 若 Retrofit `baseUrl` 配置为 `http://<host>:<port>/api/app/`，则接口声明为相对路径 **`report/...`**（勿重复 `/api/app`）。

---

## 2. 通用约定

### 2.1 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": { }
}
```

- **`code == 0`**：成功，`data` 为业务数据。
- **`code != 0`**：失败，**优先用 `message` 提示用户**（可与现有登录、交易接口错误处理共用逻辑）。

### 2.2 鉴权与身份

- 当前阶段与现有接口一致：请求携带 **`userId`**（登录成功后本地保存的 `userId`）。
- 后端校验 `app_user` 为 `ACTIVE`；报表数据仅关联 **该用户已开户成功** 的客户（与 `cust_open_apply` / `cust_import_record` 开户链路一致）。
- 若后续改为 Header Token 鉴权，仅需在拦截器与 Retrofit `Interceptor` 统一调整一处。

### 2.3 日期与分页

- 日期查询参数：`from`、`to`，格式 **`yyyy-MM-dd`**（ISO 日期），选填；语义为 **`[from 当天 00:00, to+1 天 00:00)`** 左闭右开。
- 分页：`page` 从 **1** 开始，`pageSize` 默认 **20**（服务端有上限保护）。

---

## 3. App 报表接口清单

| 能力 | 方法 | 相对路径（相对 `.../api/app/`） | 必填参数 | 可选参数 |
|------|------|----------------------------------|----------|----------|
| 本人客户资料 | GET | `report/profile` | `userId` | — |
| 资产流水 | GET | `report/asset-journal` | `userId` | `from`, `to`, `page`, `pageSize` |
| 成交 | GET | `report/trades` | `userId` | `from`, `to`, `page`, `pageSize` |
| 风控事件 | GET | `report/risk-events` | `userId` | `from`, `to`, `page`, `pageSize` |
| 通知聚合 | GET | `report/notifications` | `userId` | `limit`（默认 30，上限由服务端约束） |

**未迁移路径（请继续沿用原文档）**：

- 委托列表：`GET /trade/orders?userId=...`（与报表并列，勿重复实现两套委托列表）。
- 资金账户、资产总览、持仓：`/trade/fund-accounts`、`/assets/overview`、`/assets/positions` 等。

---

## 4. 各接口说明与 `data` 结构

### 4.1 `GET report/profile?userId={id}`

**用途**：「我的 / 账户」页展示本人是否已开户及基础资料（证件号脱敏）。

**成功 `data` 示例**（已开户）：

```json
{
  "opened": true,
  "customer": {
    "customerCode": "C202601010001",
    "customerName": "张三",
    "idType": "ID_CARD",
    "idNoMasked": "1101****1234",
    "acctClsCode": "NORMAL",
    "customerStatus": "ACTIVE",
    "riskLevel": "R2",
    "openDate": "2026-01-01"
  }
}
```

未开户时：`opened` 为 `false`，`customer` 为 `null`。

---

### 4.2 `GET report/asset-journal?userId=&from=&to=&page=&pageSize=`

**用途**：资产变动流水（与资金、持仓变动相关的台账）。

**成功 `data` 结构**（分页）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `page` | int | 当前页 |
| `pageSize` | int | 每页条数 |
| `total` | int | 总条数 |
| `list` | array | 流水明细；字段含 `journalNo`、`assetType`、`changeType`、`direction`、`changeAmount`、`occurTime`、`fundAccountNo` 等 |

未开户：`total` 为 0，`list` 为空数组。

---

### 4.3 `GET report/trades?userId=&from=&to=&page=&pageSize=`

**用途**：本人成交记录列表（分页）。

**`data` 结构**：同 4.2 的 `page` / `pageSize` / `total` / `list`。`list` 中单条含 `tradeNo`、`orderNo`、`marketCode`、`securityCode`、`tradeDirection`、`tradePrice`、`tradeQty`、`tradeAmount`、`commissionAmount`、`taxAmount`、`netSettleAmount`、`tradeTime` 等。

---

### 4.4 `GET report/risk-events?userId=&from=&to=&page=&pageSize=`

**用途**：与本人客户相关的风控事件（分页）。

**`data` 结构**：同上。`list` 中含 `eventNo`、`eventType`、`riskLevel`、`hitMessage`、`eventStatus`、`ruleCode`、`ruleName`、`createdAt` 等。

未开户：空分页。

---

### 4.5 `GET report/notifications?userId=&limit=`

**用途**：「消息中心」类列表：合并 **开户申请动态** 与 **本人风控提示**，按时间倒序（服务端已排序，客户端也可再排）。

**成功时 `data` 为数组**，元素字段因渠道不同略有差异：

| `channel` | 含义 | 常见字段 |
|-----------|------|----------|
| `OPEN_ACCOUNT` | 开户相关 | `title`、`refNo`（申请号）、`status`、`createdAt` |
| `RISK` | 风控提示 | `title`、`refNo`（事件号）、`riskLevel`、`message`、`status`、`createdAt` |

---

## 5. Android 工程修改要点

1. **Retrofit**：新增 `AppReportApi`（或扩展现有 `ApiService`），`@GET("report/profile")` 等，**`baseUrl` 仍以 `/api/app/` 结尾**。
2. **Repository / ViewModel**：登录后持有 `userId`，各报表页拉数时传入 `userId`。
3. **UI**：  
   - 「我的」：调 `report/profile`；  
   - 「消息 / 通知」：调 `report/notifications`；  
   - 「流水 / 成交 / 风控」：列表 + 日期筛选 + 分页加载更多（`page++`）。
4. **错误处理**：`code != 0` 展示 `message`；若文案含登录失效，与全局会话过期逻辑一致（清会话、回登录）。

### 5.1 Kotlin 接口示例

```kotlin
interface AppReportApi {
    @GET("report/profile")
    suspend fun profile(@Query("userId") userId: Long): ApiEnvelope<Map<String, Any?>>

    @GET("report/asset-journal")
    suspend fun assetJournal(
        @Query("userId") userId: Long,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiEnvelope<Map<String, Any?>>

    @GET("report/trades")
    suspend fun trades(
        @Query("userId") userId: Long,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiEnvelope<Map<String, Any?>>

    @GET("report/risk-events")
    suspend fun riskEvents(
        @Query("userId") userId: Long,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiEnvelope<Map<String, Any?>>

    @GET("report/notifications")
    suspend fun notifications(
        @Query("userId") userId: Long,
        @Query("limit") limit: Int = 30
    ): ApiEnvelope<List<Map<String, Any?>>>
}
```

`ApiEnvelope` 与现有模块保持一致即可，例如：

```kotlin
data class ApiEnvelope<T>(val code: Int, val message: String, val data: T?)
```

日期建议：`LocalDate` → `DateTimeFormatter.ISO_LOCAL_DATE` 转成 `String` 传入 `from` / `to`。

---

## 6. 附录：柜台报表（非 Android 常规场景）

柜台端 Base 为 **`/api/operator`**，需请求头 **`X-Operator-Token`**。报表子路径 **`/api/operator/report/**`**，支持按客户代码、证件号、资金账号、证券账号、委托号、成交号及日期区间等组合查询（由服务端统一解析，禁止客户端拼接 SQL）。接口包括：`/customers`、`/orders`、`/trades`、`/asset-journal`、`/risk-events`、`/operation-logs`、`/dispatches`、`/cancel-requests`、`/fee-details` 等，实现见后端 **`OperatorReportController`**。

---

## 7. 后端实现索引（供联调对照）

| 模块 | 路径 |
|------|------|
| App 报表 Controller | `backend/src/main/java/com/financial/operator/controller/AppReportController.java` |
| 统一查询服务 | `backend/src/main/java/com/financial/operator/service/UnifiedBusinessQueryService.java` |
| 柜台报表 Controller | `backend/src/main/java/com/financial/operator/controller/OperatorReportController.java` |

---

*文档版本与仓库接口一致；若端口或网关调整，请先更新 **`ANDROID_环境与BaseURL对齐说明.md`**。*
