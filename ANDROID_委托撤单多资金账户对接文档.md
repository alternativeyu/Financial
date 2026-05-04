# Android 下单与撤单页面设计对接文档（多资金账户）

> **环境与 Base URL（端口、模拟器、网关）**：请先阅读 **`ANDROID_环境与BaseURL对齐说明.md`**，再按本文对接接口。

## 1. 文档目标

用于指导 Android 端完成以下能力：

1. 下单页面设计与交互流程
2. 下单前风控预校验
3. 多资金账户选择与绑定
4. 撤单流程与可撤判断
5. 接口参数规则、错误码处理与幂等策略

---

## 2. 基础约定

- Base URL: `http://127.0.0.1:28480/api/app`（默认端口，可按部署修改）
- 统一响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code=0` 表示成功，非 0 表示失败。
- 建议 Android 统一错误弹窗内容：优先展示 `message`。

---

## 3. 页面设计建议

## 3.1 下单页（TradeOrderActivity）

建议页面分区：

1. 顶部区：资金账户选择
2. 中间区：下单表单
3. 下方区：风控结果提示 + 提交按钮

建议字段：

- 资金账户（下拉选择，来源于接口）
- 市场（SH/SZ）
- 证券代码
- 买卖方向（BUY/SELL）
- 委托价格
- 委托数量

按钮：

- `预校验`
- `提交委托`

交互规则：

1. 进入页面先加载资金账户列表
2. 必须先选资金账户再允许预校验和下单
3. 预校验通过后再允许提交委托
4. 提交成功后跳转“委托详情/委托列表”

---

## 3.2 委托列表页（OrderListActivity）

建议字段：

- 委托编号、证券代码、方向、价格、委托量、已成量、状态、时间
- 对可撤单状态显示“撤单”按钮

建议可撤判断（客户端展示层）：

- 仅当后端状态为 `REPORTED` 或 `PART_FILLED` 且仍有未成交数量时展示可撤入口
- 最终是否允许撤单以后端接口返回为准

---

## 3.3 撤单确认弹窗

字段：

- 委托编号（只读）
- 撤单原因（可选）

按钮：

- `确认撤单`
- `取消`

---

## 4. 接口清单

1. 查询资金账户：`GET /trade/fund-accounts`
2. 风控预校验：`POST /trade/risk/precheck`
3. 提交委托：`POST /trade/orders`
4. **查询本人 App 委托列表（含 `canCancel`）**：`GET /trade/orders`
5. 提交撤单（**标准自动流程**：客户端发起后由后端同步完成撤单模拟，**无需**柜台人工点撤）：`POST /trade/orders/{orderNo}/cancel`

> 与生产券商一致：投资者在 App「委托/订单」中对未成交委托点撤单，由交易网关自动向交易所申报；本演示后端在同一请求内完成撤单与资金/持仓解冻的模拟。

---

## 5. 资金账户接口

### 5.1 查询可用资金账户

- **Method**: `GET`
- **Path**: `/trade/fund-accounts`
- **Query**: `userId`（必填）

示例：

`GET /api/app/trade/fund-accounts?userId=10001`

返回示例：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "fundAccountNo": "FA20260430010001",
      "currentBalance": 100000.0000,
      "availableBalance": 85000.0000,
      "frozenBalance": 15000.0000,
      "status": "NORMAL",
      "openedAt": "2026-05-01T09:30:00"
    }
  ]
}
```

客户端规则：

- 只允许选择 `status = NORMAL` 的账户
- 若接口返回空列表，提示“暂无可交易资金账户”

---

## 6. 风控预校验接口

### 6.1 请求

- **Method**: `POST`
- **Path**: `/trade/risk/precheck`

请求体：

```json
{
  "userId": 10001,
  "fundAccountNo": "FA20260430010001",
  "marketCode": "SH",
  "securityCode": "600000",
  "tradeDirection": "BUY",
  "price": 10.25,
  "quantity": 100
}
```

参数规则：

- `fundAccountNo` 必填，必须属于当前用户开户后的客户
- `marketCode`：`SH` / `SZ`
- `tradeDirection`：`BUY` / `SELL`（也兼容 `B` / `S`）
- `price > 0`
- `quantity > 0` 且需符合最小交易单位（手数）

### 6.2 返回说明

通过时：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "passed": true,
    "customerId": 20001,
    "violations": []
  }
}
```

不通过时：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "passed": false,
    "customerId": 20001,
    "violations": [
      { "code": "INSUFFICIENT_CASH", "message": "可用资金不足" }
    ]
  }
}
```

---

## 7. 下单接口

### 7.1 请求

- **Method**: `POST`
- **Path**: `/trade/orders`

请求体：

```json
{
  "userId": 10001,
  "fundAccountNo": "FA20260430010001",
  "marketCode": "SH",
  "securityCode": "600000",
  "tradeDirection": "BUY",
  "price": 10.25,
  "quantity": 100,
  "requestSeqNo": "APP_ORDER_20260501_000001"
}
```

参数规则：

- 所有字段必填
- `requestSeqNo` 必须全局唯一（至少在客户端本地唯一）
- 推荐格式：`APP_ORDER_${timestamp}_${random}`

### 7.2 返回

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "orderId": 123456789,
    "orderNo": "OD20260501000001",
    "orderStatus": "REPORTED",
    "dispatchNo": "DP20260501000001",
    "fundAccountNo": "FA20260430010001",
    "frozenCash": 1025.0000,
    "frozenQty": 0,
    "idempotent": false
  }
}
```

说明：

- 同一 `requestSeqNo` 重复提交，返回同一笔委托结果，`idempotent=true`
- 买入冻结资金；卖出冻结持仓

---

## 8. 撤单接口

### 8.1 请求

- **Method**: `POST`
- **Path**: `/trade/orders/{orderNo}/cancel`

请求体：

```json
{
  "userId": 10001,
  "requestSeqNo": "APP_CANCEL_20260501_000001",
  "cancelReason": "用户主动撤单"
}
```

参数规则：

- `orderNo` 路径参数必填
- `requestSeqNo` 必填且唯一（幂等）
- `cancelReason` 可选

### 8.2 返回

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "cancelId": 987654321,
    "cancelNo": "CX20260501000001",
    "cancelStatus": "CANCELED",
    "orderNo": "OD20260501000001",
    "orderStatus": "CANCELED",
    "releasedCash": 1025.0000,
    "releasedQty": 0,
    "idempotent": false
  }
}
```

---

## 9. 关键错误码与业务错误

通用错误：

- `1001`：参数错误
- `1005`：登录态失效
- `2001`：客户未开户（交易前置不满足）

风控违规（`violations[].code`）常见值：

- `FUND_ACCOUNT_INVALID`：资金账户不存在或不属于当前客户
- `ACCOUNT_FROZEN`：资金账户状态异常
- `SECURITY_NOT_FOUND`：证券不存在
- `SECURITY_NOT_LISTED`：证券不可交易
- `LOT_SIZE_INVALID`：数量不符合交易单位
- `PRICE_UPPER_LIMIT`：超过涨停
- `PRICE_LOWER_LIMIT`：低于跌停
- `INSUFFICIENT_CASH`：可用资金不足
- `INSUFFICIENT_POSITION`：可卖持仓不足
- `DIRECTION_INVALID`：买卖方向非法

撤单常见失败提示（`message`）：

- `当前委托状态不允许撤单`
- `该委托已有撤单申请处理中`

---

## 10. Android 侧推荐流程（强烈建议）

1. 页面初始化：
   - 调 `fund-accounts` 获取可选资金账户
   - 默认选最近使用账户
2. 用户填单：
   - 选择资金账户 + 市场 + 方向 + 代码 + 价格 + 数量
3. 点击预校验：
   - 调 `risk/precheck`
   - 若不通过，逐条展示违规原因
4. 点击提交委托：
   - 生成 `requestSeqNo`
   - 调 `trade/orders`
5. 进入委托列表：
   - 展示订单状态，满足条件可发起撤单
6. 点击撤单：
   - 生成新的撤单 `requestSeqNo`
   - 调 `orders/{orderNo}/cancel`

---

## 11. 实现细节建议

1. `requestSeqNo` 持久化到本地（防重复点击和重试）
2. 价格使用字符串输入，提交前转 decimal，避免精度问题
3. 数量仅允许正整数
4. 提交按钮防抖（2 秒内禁用）
5. 网络重试场景优先复用同一 `requestSeqNo`

---

## 12. 测试用例建议

1. 正常买入下单成功
2. 正常卖出下单成功
3. 资金不足拦截
4. 持仓不足拦截
5. 非法资金账户拦截（`FUND_ACCOUNT_INVALID`）
6. 重复 `requestSeqNo` 幂等验证
7. 可撤单状态撤单成功
8. 不可撤单状态撤单失败
