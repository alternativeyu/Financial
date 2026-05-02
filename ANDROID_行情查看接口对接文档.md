# Android 行情查看接口对接文档

## 目标

使用当前数据库中的 `md_security` + `md_market_quote` 数据，完成 Android 端行情列表与详情展示。

---

## 基础信息

- **Base URL**: `http://127.0.0.1:8080/api/app`
- **鉴权**: 当前接口无需额外 token（后续可加）
- **返回格式**:

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

---

## 1) 行情列表接口

- **Method**: `GET`
- **Path**: `/market/quotes`

### Query 参数

- `market`（可选）: `SH` / `SZ` / `1` / `0`
- `keyword`（可选）: 代码或名称模糊搜索
- `page`（可选，默认1）
- `pageSize`（可选，默认20，最大100）

### 示例请求

```http
GET /api/app/market/quotes?market=SH&page=1&pageSize=20
```

### 示例返回

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "page": 1,
    "pageSize": 20,
    "total": 120,
    "list": [
      {
        "marketCode": "1",
        "market": "SH",
        "marketName": "沪A",
        "securityCode": "600000",
        "securityName": "浦发银行",
        "currentPrice": 10.3200,
        "prevClosePrice": 10.1800,
        "changeAmount": 0.1400,
        "changePct": 1.3752,
        "upperLimitPrice": 11.1980,
        "lowerLimitPrice": 9.1620,
        "volume": 5421300,
        "amount": 56001000.0000,
        "quoteTime": "2026-05-01T20:58:10"
      }
    ]
  }
}
```

---

## 2) 单只行情详情接口

- **Method**: `GET`
- **Path**: `/market/quotes/{market}/{securityCode}`

### Path 参数

- `market`: `SH` / `SZ`（也兼容 `1` / `0`）
- `securityCode`: 证券代码（如 `600000`）

### 示例请求

```http
GET /api/app/market/quotes/SH/600000
```

### 示例返回

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "marketCode": "1",
    "market": "SH",
    "marketName": "沪A",
    "securityCode": "600000",
    "securityName": "浦发银行",
    "currentPrice": 10.3200,
    "prevClosePrice": 10.1800,
    "changeAmount": 0.1400,
    "changePct": 1.3752,
    "upperLimitPrice": 11.1980,
    "lowerLimitPrice": 9.1620,
    "volume": 5421300,
    "amount": 56001000.0000,
    "quoteTime": "2026-05-01T20:58:10"
  }
}
```

---

## 错误码

- `1001`：参数错误（market 或 securityCode 不合法）
- `2001`：证券不存在
- `9999`：系统异常（由统一异常处理返回）

---

## Android 接入建议

1. 首页列表调用 `/market/quotes`，默认 `market=SH` 或全市场
2. 搜索框映射 `keyword`
3. 点击列表项进入详情页，调 `/market/quotes/{market}/{securityCode}`
4. 展示涨跌颜色：
   - `changeAmount > 0` 红色
   - `< 0` 绿色
   - `= 0` 灰色
