# Android 开户审核结果对接文档

## 目标

Android 端仅需实现两点：

1. 提示当前申请是否审核通过（通过/未通过/审核中）
2. 审核通过后，进入“开户结果页”展示账户结果

---

## 一、接口清单（最小方案）

> 说明：以下为 Android 建议调用接口。  
> 当前项目已实现提交接口；查询状态/结果建议按本文件补齐到 `AppApiController`（`/api/app`）后给 Android 使用。

### 1) 提交开户申请（已实现）

- **Method**: `POST`
- **Path**: `/api/app/open-account/apply`
- **Content-Type**: `application/json`

请求体：

```json
{
  "userId": 1777530743937050,
  "customerName": "lyb",
  "idType": "ID_CARD",
  "idNo": "211402200505270016",
  "mobile": "18109890527",
  "acctClsCode": "NORMAL",
  "shareholderMarket": "SH"
}
```

返回（成功）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "applyId": 1777530743937999,
    "applyNo": "OA20260430012345",
    "status": "WAIT_AUDIT"
  }
}
```

---

### 2) 查询我的申请状态（建议新增）

- **Method**: `GET`
- **Path**: `/api/app/open-account/applications/latest?userId={userId}`

用途：Android 首页/进度页只判断“是否通过”时调用。

建议返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "applyId": 1777530743937999,
    "applyNo": "OA20260430012345",
    "status": "OPENED",
    "statusText": "审核通过",
    "auditComment": null,
    "updatedAt": "2026-04-30T18:02:41"
  }
}
```

状态建议映射：

- `WAIT_AUDIT` / `IMPORTED` / `IN_REVIEW` -> 审核中
- `REJECTED` -> 审核未通过
- `OPENED` -> 审核通过

---

### 3) 查询开户结果详情（建议新增）

- **Method**: `GET`
- **Path**: `/api/app/open-account/applications/{applyId}/result`

用途：当状态为 `OPENED` 时，Android 跳转“开户结果页”调用。

建议返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "applyId": 1777530743937999,
    "applyNo": "OA20260430012345",
    "status": "OPENED",
    "customer": {
      "customerCode": "C202604302401",
      "customerName": "lyb",
      "acctClsCode": "NORMAL"
    },
    "fundAccount": {
      "fundAccountNo": "FA20260430807849",
      "currentBalance": 100000.0
    },
    "initCashBalance": 100000.0,
    "initPositionCount": 0,
    "shareholderAccounts": [
      {
        "marketCode": "1",
        "marketName": "沪A",
        "shareholderAccountNo": "A596028857",
        "status": "NORMAL",
        "openedAt": "2026-04-30T18:02:41"
      }
    ]
  }
}
```

---

## 二、数据库字段对照（Android 关心）

## 1) 申请主表：`cust_open_apply`

用于申请状态、审核结果文案、市场信息。

- `id` -> `applyId`
- `apply_no` -> `applyNo`
- `user_id` -> 用户ID
- `customer_name` -> 客户姓名
- `id_type` -> 证件类型
- `id_no` -> 证件号
- `mobile` -> 手机号
- `acct_cls_code` -> 账户类别（`NORMAL`/`VIP`）
- `shareholder_market` -> 开户市场（`SH`/`SZ`）
- `apply_status` -> 状态（`WAIT_AUDIT/IMPORTED/IN_REVIEW/REJECTED/OPENED`）
- `audit_comment` -> 审核意见/拒绝原因
- `created_at`, `updated_at`

## 2) 客户表：`cust_customer`

- `customer_code` -> 客户编码
- `customer_name`
- `acct_cls_code`

## 3) 资金账户表：`acct_fund_account`

- `fund_account_no` -> 资金账户号
- `current_balance` -> 当前余额

## 4) 股东账户表：`acct_shareholder_account`

- `market_code` -> 市场（`1=沪A`，`0=深A`）
- `shareholder_account_no` -> 股东账号
- `account_status` -> 状态
- `opened_at` -> 开通时间

---

## 三、Android 页面逻辑（最简）

1. 提交申请成功后保存 `applyId/applyNo`
2. 进入进度页，轮询“最新申请状态接口”
3. 当 `status == OPENED` 时显示按钮“查看开户结果”
4. 点击按钮后调用“开户结果详情接口”展示结果页

---

## 四、错误码（当前已使用）

- `1001` 参数错误
- `1005` 登录态失效
- `2001` 开户申请重复
- `2002` 手机号格式错误
- `2003` 证件号格式错误
- `2004` 市场参数错误（仅支持 `SH/SZ`）
- `9999` 系统异常

Android 建议：优先展示接口返回 `message`。
