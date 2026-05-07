# 基于 C/S 架构的 A 股模拟证券交易系统 — 概要设计（与当前实现对齐）

> **文档性质**：在《CS设计文档》（FTS-CS-DESIGN-001 等）基础上的**对齐修订稿**，以本仓库 **Financial** 工程为准。  
> **对齐日期**：以仓库当前实现为准。  
> **范围说明**：本文档**不包含**「报表查询」与「风控子系统」的专门设计（与您的要求一致）。其中：  
> - **报表**：`/api/app/report/*`、`/api/operator/report/*` 等接口存在，但不作为本文档展开对象。  
> - **风控**：不展开 `risk_rule` / `risk_event` 及柜台风控处置流程；**仅**描述已实现之**交易规则硬校验**（委托/预检/清算中的参数与资金股份校验，见 `TradeController`、`AppApiController.tradeRiskPrecheck`）。

---

## 1 目的和范围

### 1.1 目的

说明当前实现的 **C/S 架构迷你证券交易系统** 的总体结构、功能边界、核心流程与主要数据表，为开发、联调、测试与演示提供依据。

### 1.2 业务范围（与实现对齐）

- A 股现货**模拟**交易；不接入真实交易所、不做真实资金划转。
- **安卓用户端**：通过 HTTP(S) 调用 `operator-backend` 的 `/api/app/**`。
- **柜台端**：本仓库以 **Web 前端（Vue）+ 运营 API** 为主（`/api/operator/**`）；原设计书中的「桌面客户端」可视为等价客户端形态。
- **交易后台**：`operator-backend`（Spring Boot 单体），端口默认 **28480**（`server.port` / `SERVER_PORT`）。
- **模拟报盘受理（可选独立进程）**：`mock-exchange` 模块（默认端口 **28482**）。当 `financial.mock-exchange.base-url` 非空时，委托在**本进程事务提交后**由该服务写 `rpt_order_reply` 并更新 `rpt_order_dispatch` / `trd_order`；为空时仍在 `operator-backend` 进程内完成受理（见 `MockExchangeClient`、`TradeController`）。

---

## 2 系统架构（与实现对齐）

### 2.1 逻辑架构

| 层级 | 实现说明 |
|------|----------|
| 安卓用户端 | 移动 App，调用 `/api/app/*`；鉴权主要为 **App 业务参数中的 `userId` + 服务端校验 `app_user` 状态**（会话 token 存 `app_login_session`）。 |
| 柜台 Web 端 | `frontend/` Vue 应用，调用 `/api/operator/*`；鉴权为 **`X-Operator-Token`**，会话表 `op_login_session`。 |
| 接入层 | 可由 **Nginx / 独立 API 网关** 反向代理；应用内另有 **`ApiOrderRateLimitFilter`**（Redis 计数，针对下单 POST 限流，见 `application.yml` `financial.ratelimit.*`）。 |
| 交易后台 | **`operator-backend`**：统一 JDBC 访问 MySQL，Redis（缓存/限流），可选 RabbitMQ（成交清算等消息，见 `financial.messaging.enabled`）。 |
| 模拟交易所（可选） | **`mock-exchange`**：独立 JVM，与后台**共用同一 MySQL 库**；HTTP `POST /exchange/v1/order-accept`；可选 `X-Mock-Exchange-Token` 与 `financial.mock-exchange.shared-secret` 一致。 |

原设计书中的「模拟报盘与成交回报**引擎**」在当前实现中：**报盘受理（ORDER_ACCEPT）** 可由 `mock-exchange` 承担；**撤单报盘、撮合成交** 仍以 `operator-backend` 内逻辑与运营接口为主（按现有代码为准）。

### 2.2 与原文档差异摘要

| 原设计表述 | 当前实现 |
|------------|----------|
| 独立的 API 网关模块 | 未单独拆 Maven 模块；可用外部网关 + 应用内 Filter。 |
| 模拟报盘引擎进程内嵌 | **已支持**独立进程 `mock-exchange` 写回报，由配置开关。 |
| 风控子系统（规则表、风险事件、柜台处置） | **本文档不包含**；交易硬规则在下单/预检/清算中实现。 |
| 报表查询统一服务 | **本文档不包含**；实现见 `UnifiedBusinessQueryService` 与 `*ReportController`。 |
| 安卓通知中心 / 设备表等 | 以实际表与接口为准：`app_notice` 等若在库中存在可继续扩展；当前文档仅列代码中**直接出现**的核心表。 |

---

## 3 主要状态与流转（委托）

与 `TradeController`、`rpt_order_dispatch`、`trd_order` 等实现一致（状态字面值以库中为准）：

- **委托**：下单后 `trd_order.order_status` 经受理至 **REPORTED**；存在 **PART_FILLED**、终态等（成交、撤单路径见代码）。
- **报盘**：`rpt_order_dispatch`，`dispatch_type` 含 **ORDER**、**CANCEL**、**MATCH** 等；`dispatch_status` 含 **SENT**、**ACCEPTED** 等。
- **回报**：`rpt_order_reply`，如 **ORDER_ACCEPT**。
- **撤单**：`trd_cancel_request` + 对应 `rpt_order_dispatch` / `rpt_order_reply` 流程。
- **成交**：`trd_trade`、`trd_fee_detail`、`acct_asset_journal` 更新及委托累计字段。

---

## 4 功能与接口（与路径对齐）

### 4.1 安卓用户端（`/api/app`）

| 功能 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 注册 | POST | `/api/app/auth/register` | `AppApiController` |
| 登录 | POST | `/api/app/auth/login` | 返回 `app_*` token |
| 开户申请 | POST | `/api/app/open-account/apply` | |
| 最近申请 | GET | `/api/app/open-account/applications/latest` | |
| 开户结果 | GET | `/api/app/open-account/applications/{applyId}/result` | |
| 交易资金户列表 | GET | `/api/app/trade/fund-accounts` | |
| 交易风控预检 | POST | `/api/app/trade/risk/precheck` | 规则试算，**非**独立风控引擎文档范围 |
| 行情列表 | GET | `/api/app/market/quotes` | |
| 行情详情 | GET | `/api/app/market/quotes/{market}/{securityCode}` | |
| 资产总览 | GET | `/api/app/assets/overview` | |
| 持仓列表 | GET | `/api/app/assets/positions` | |

**委托与撤单**（`/api` 前缀在 `TradeController` 上，完整路径如下）：

| 功能 | 方法 | 路径 |
|------|------|------|
| App 下单 | POST | `/api/app/trade/orders` |
| App 委托列表 | GET | `/api/app/trade/orders` |
| App 撤单 | POST | `/api/app/trade/orders/{orderNo}/cancel` |

### 4.2 柜台操作员端（`/api/operator`）

| 功能 | 方法 | 路径 |
|------|------|------|
| 运营登录 | POST | `/api/operator/auth/login` |
| 开户申请列表等 | GET/POST | `/api/operator/opening/*`（见 `OperatorController`） |
| 代客下单 | POST | `/api/operator/trade/orders` |
| 委托列表 | GET | `/api/operator/trade/orders` |
| 撤单 | POST | `/api/operator/trade/orders/{orderNo}/cancel` |
| 模拟成交 | POST | `/api/operator/trade/orders/{orderNo}/simulate-match` |
| 手工成交回报 | POST | `/api/operator/trade/orders/{orderNo}/fill-reports` |
| 字典项 | GET | `/api/operator/meta/dict-items` |

### 4.3 模拟交易所（`mock-exchange`）

| 功能 | 方法 | 路径 |
|------|------|------|
| 报盘受理 | POST | `/exchange/v1/order-accept` |

请求体字段：`dispatchId`, `orderId`, `orderNo`（见 `OrderAcceptRequest`）。

### 4.4 公共算法（与实现一致）

- **买入冻结**（含费用）：`orderAmount + 预估佣金 + 买入侧预估印花税（规则表可为 0）`，见 `TradingFeeRuleService` + `TradeController.submitOrderInternal`。
- **卖出冻结数量**：等于委托数量。
- **成交金额、佣金、印花税、净额、冻结释放**：见 `applyTradeSettlementInternal` 与 `TradingFeeRuleService`。

---

## 5 数据库与表（代码路径直接涉及）

以下表在 `operator-backend` / `mock-exchange` 代码中出现，作为对齐清单（**不等同**于原「27 张表」全量清单；未在 SQL 片段中出现的表未强行列入）：

**用户与接入**：`app_user`, `app_login_session`  

**开户与客户**：`cust_open_apply`, `cust_customer`, `cust_import_batch`, `cust_import_record`, `cust_open_audit_log`  

**账户与资产**：`acct_fund_account`, `acct_shareholder_account`, `acct_position`, `acct_asset_journal`  

**主数据与费用规则**：`md_security`, `md_market_quote`, `md_commission_rule`, `md_tax_rule`（及字典相关表由 `MetaController` / 缓存读取）  

**交易与报盘**：`trd_order`, `trd_trade`, `trd_cancel_request`, `trd_fee_detail`, `rpt_order_dispatch`, `rpt_order_reply`  

**运营与审计**：`op_login_session`, `sys_operation_log`  

**风控 / 报表**：库中可能存在 `risk_*` 等表及报表查询所用表，**不在本文档展开**。

---

## 6 性能、安全与部署（与实现对齐）

- **缓存**：Redis 用于字典/行情等（见 `RedisInfraCacheConfig`、`MdDictQueryService`、`AppMarketQuoteQueryService`）。
- **限流**：`ApiOrderRateLimitFilter`，仅对 **`POST /api/app/trade/orders`** 与 **`POST /api/operator/trade/orders`** 按 IP 计数；Redis 异常时 fail-open。
- **消息**：RabbitMQ 可选；`financial.messaging.enabled` 控制监听器是否启动。
- **安全**：运营接口依赖 `X-Operator-Token`；App 接口校验 `userId` 与 `app_user`；**敏感数据不落文档**（数据库密码等用环境变量覆盖）。

---

## 7 测试与演示顺序（保留原方法论，对齐现接口）

仍可采用原文所述顺序：**用户端真实发起 → 柜台审核处置 → 后台自动清算**，将具体操作替换为：

1. App：`/api/app/auth/register` → `/api/app/auth/login` → `/api/app/open-account/apply`  
2. 柜台：`/api/operator/auth/login` → `/api/operator/opening/applications/*` 审核通过  
3. App：`/api/app/trade/fund-accounts`、`/api/app/trade/orders`（下单）  
4. 若启用 `mock-exchange`：确认 `POST /exchange/v1/order-accept` 成功后委托为已报等状态  
5. 柜台：`/api/operator/trade/orders/{orderNo}/simulate-match` 或 `fill-reports` 完成成交演示  
6. App：`/api/app/trade/orders`、资产类接口核对**不包含报表章节**

---

## 8 修订说明与源文档

- **源 Word 路径**（用户本地）：`d:\xwechat_files\...\CS设计文档.docx`  
- **本对齐稿路径**（仓库）：`docs/CS设计说明书_与实现对齐.md`  
- 可将本 Markdown 章节粘贴回 Word，或保留为设计库旁路「实现对照」文档，随代码迭代更新。

---

## 9 与原 Word 目录映射（便于合并）

| 原目录章节（摘录） | 本对齐稿位置 |
|--------------------|--------------|
| 1 引言 | §1 |
| 2 设计概述 / 架构 | §2 |
| 2.1.2 主要状态变化 | §3 |
| 3 功能及处理流程（安卓/柜台/开户/委托/撤单/成交） | §4 |
| 3.7 风控处理 | **不纳入**（仅§1脚注硬校验） |
| 3.8 报表查询 | **不纳入** |
| 4 数据库概要 | §5 |
| 5 性能、安全与部署 | §6 |
| 6 测试与演示 | §7 |

如需把本稿**反写**进 `.docx` 并保持样式，可在 Word 中使用「插入 → 文件中的文字」导入 Markdown（或使用 Pandoc），再手工调整表格与图片编号。
