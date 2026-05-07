# -*- coding: utf-8 -*-
"""Generate 测试用例执行映射.csv from 系统功能测试用例_模板对齐.csv (adds API/priority columns)."""
import csv
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "系统功能测试用例_模板对齐.csv"
OUT = ROOT / "测试用例执行映射.csv"

PRIORITY = {
    "TC-TRD-014": "P0",
    "TC-TRD-013": "P0",
    "TC-APP-004": "P0",
    "TC-OPS-001": "P0",
    "TC-MEX-001": "P0",
    "TC-E2E-001": "P0",
    "TC-RPT-001": "P0",
    "TC-METH-001": "P0",
    "TC-TFLOW-001": "P0",
    "TC-OPEN-001": "P0",
}

DEFAULT_P1_PREFIXES = (
    "TC-TRD-",
    "TC-CXL-",
    "TC-SET-0",
    "TC-PRC-",
    "TC-APP-",
    "TC-MEX-0",
)


def priority_for(tid: str) -> str:
    if tid in PRIORITY:
        return PRIORITY[tid]
    if tid.startswith(DEFAULT_P1_PREFIXES) and tid not in ("TC-NF-", "TC-DATA-", "TC-E2E-"):
        return "P1"
    if tid.startswith("TC-OPS-") or tid.startswith("TC-RPT-") or tid.startswith("TC-META-"):
        return "P1"
    if tid.startswith("TC-SEC-"):
        return "P2"
    if tid.startswith("TC-DATA-") or tid.startswith("TC-NF-"):
        return "P2"
    if tid.startswith("TC-E2E-"):
        return "P0" if tid == "TC-E2E-001" else "P1"
    if tid.startswith(
        ("TC-OPEN-", "TC-TFLOW-", "TC-CFLOW-", "TC-FFLOW-", "TC-RISK-", "TC-QRY-", "TC-MEX-INT-")
    ):
        return "P1"
    if tid.startswith("TC-METH-"):
        return "P0"
    return "P2"


def api_row(tid: str, scene: str) -> tuple[str, str, str, str, str, str]:
    """Returns: module, http, path, auth, key_params, expected_http, expected_code_hint"""
    if tid.startswith("TC-TRD-"):
        if tid in ("TC-TRD-020", "TC-TRD-023"):
            return (
                "交易",
                "GET",
                "/api/app/trade/orders",
                "userId 查询参数",
                "orderListCategory/page/pageSize 等",
                "200 或业务失败",
                "见断言",
            )
        if tid == "TC-TRD-021" or tid == "TC-TRD-022":
            return (
                "交易",
                "GET",
                "/api/operator/trade/orders",
                "X-Operator-Token",
                "orderStatusGroup/page/pageSize",
                "200 或业务失败",
                "见断言",
            )
        if tid.startswith("TC-TRD-017"):
            return (
                "交易",
                "POST",
                "/api/operator/trade/orders",
                "X-Operator-Token",
                "JSON: customerId,fundAccountNo,marketCode,securityCode,tradeDirection,price,quantity,requestSeqNo",
                "200",
                "code=0 成功 / 1001 失败",
            )
        return (
            "交易",
            "POST",
            "/api/app/trade/orders 或 /api/operator/trade/orders",
            "App: JSON userId；Operator: X-Operator-Token",
            "见模板用例预置与操作步骤",
            "200",
            "code=0 或 1001/2001；message 含预期关键字",
        )
    if tid.startswith("TC-MEX-INT-"):
        return (
            "模拟所集成",
            "POST/运维",
            "/exchange/v1/order-accept 或补偿",
            "Token/网络",
            "并发、超时、宕机恢复、版本兼容",
            "200/失败",
            "见模板",
        )
    if tid.startswith("TC-MEX-"):
        return (
            "模拟交易所",
            "POST",
            "/exchange/v1/order-accept",
            "可选 X-Mock-Exchange-Token",
            "JSON: dispatchId,orderId,orderNo",
            "200/400/401",
            "body.code=0 成功",
        )
    if tid.startswith("TC-CXL-"):
        return (
            "撤单",
            "POST",
            "/api/app/trade/orders/{orderNo}/cancel 或 /api/operator/trade/orders/{orderNo}/cancel",
            "App: body userId；Operator: X-Operator-Token",
            "requestSeqNo,cancelReason",
            "200",
            "code=0 或 1001/2001",
        )
    if tid.startswith("TC-SET-"):
        return (
            "清算成交",
            "POST",
            "/api/operator/trade/orders/{orderNo}/simulate-match 或 .../fill-reports",
            "X-Operator-Token",
            "fillQty,fillPrice,requestSeqNo,externalTradeNo 可选",
            "200",
            "code=0 或 1001",
        )
    if tid.startswith("TC-PRC-"):
        return (
            "预检",
            "POST",
            "/api/app/trade/risk/precheck",
            "无",
            "JSON: userId,fundAccountNo,marketCode,securityCode,tradeDirection,price,quantity",
            "200",
            "code=0 且 violations；或 1001",
        )
    if tid.startswith("TC-APP-"):
        if "register" in scene or tid in ("TC-APP-001", "TC-APP-002", "TC-APP-003"):
            return ("App", "POST", "/api/app/auth/register", "无", "username,password,mobile", "200", "code 见断言")
        if "登录" in scene or tid in ("TC-APP-004", "TC-APP-005"):
            return ("App", "POST", "/api/app/auth/login", "无", "account,password", "200", "code 见断言")
        if "开户" in scene or "申请" in scene or tid in ("TC-APP-006", "TC-APP-007", "TC-APP-008"):
            return ("App", "POST", "/api/app/open-account/apply", "无", "OpenAccountApplyRequest 全文", "200", "code 见断言")
        if tid == "TC-APP-009":
            return ("App", "GET", "/api/app/open-account/applications/latest", "无", "userId", "200", "code=0")
        if "结果" in scene or tid in ("TC-APP-010", "TC-APP-011", "TC-APP-012"):
            return ("App", "GET", "/api/app/open-account/applications/{applyId}/result", "无", "路径 applyId", "200", "code 见断言")
        if "资金" in scene or tid == "TC-APP-013":
            return ("App", "GET", "/api/app/trade/fund-accounts", "无", "userId", "200", "code=0")
        if "资产" in scene or tid == "TC-APP-014":
            return ("App", "GET", "/api/app/assets/overview", "无", "userId", "200", "code=0")
        if "行情" in scene or "分页" in scene:
            return ("App", "GET", "/api/app/market/quotes 或 .../quotes/{m}/{code}", "无", "market,keyword,page", "200", "code=0 或 2001")
        return ("App", "GET/POST", "见场景", "无", "见模板", "200", "见断言")
    if tid.startswith("TC-OPS-"):
        if tid == "TC-OPS-001":
            return ("运营", "POST", "/api/operator/auth/login", "无", "账号密码 JSON", "200", "成功返回 token")
        if tid in ("TC-OPS-002", "TC-OPS-003"):
            return ("运营", "POST", "/api/operator/auth/login", "无", "错误凭据", "200", "业务失败消息")
        return ("运营", "POST/GET", "/api/operator/* 开户审核等", "X-Operator-Token", "见 OperatorController", "200", "IllegalArgument 文案或 code")
    if tid.startswith("TC-RPT-"):
        if tid == "TC-RPT-002" or "App" in scene:
            return (
                "报表",
                "GET",
                "/api/app/report/profile|asset-journal|trades|risk-events|notifications",
                "无",
                "userId 及分页日期参数",
                "200",
                "code=0 或 1001",
            )
        return (
            "报表",
            "GET",
            "/api/operator/report/*",
            "X-Operator-Token",
            "各接口查询参数见 OperatorReportController",
            "200",
            "code=0 或 1001",
        )
    if tid.startswith("TC-META-"):
        return (
            "元数据",
            "GET",
            "/api/operator/meta/dict-items",
            "X-Operator-Token",
            "dictCode",
            "200/400",
            "400 body code=400 或 items",
        )
    if tid.startswith("TC-SEC-"):
        return ("安全", "POST", "/api/app/trade/orders", "无", "同 IP 高频", "429 或 200", "见断言")
    if tid.startswith("TC-DATA-"):
        return ("数据", "混合", "DB+API", "视用例", "造数", "200", "一致性")
    if tid.startswith("TC-E2E-"):
        return ("端到端", "混合", "多接口", "全链路", "脚本/人工", "200", "业务闭环")
    if tid.startswith("TC-NF-"):
        return ("非功能", "-", "-", "-", "压测/配置", "-", "指标")
    if tid.startswith("TC-METH-"):
        return ("方法论", "-", "多接口顺序", "-", "见 TC-METH-001", "-", "追溯一致")
    if tid.startswith("TC-OPEN-"):
        return (
            "开户",
            "POST/GET",
            "/api/app/open-account/* 与 /api/operator/* 审核",
            "视接口",
            "申请/审核/股东号/证件",
            "200",
            "客户与账户落库",
        )
    if tid.startswith("TC-TFLOW-"):
        return (
            "交易流程",
            "POST",
            "/api/app/trade/orders 或 /api/operator/trade/orders",
            "App/Operator",
            "冻结与状态链",
            "200",
            "流水一致",
        )
    if tid.startswith("TC-CFLOW-"):
        return (
            "撤单流程",
            "POST",
            "/api/app/trade/orders/{orderNo}/cancel 等",
            "视通道",
            "释放冻结",
            "200",
            "资产一致",
        )
    if tid.startswith("TC-FFLOW-"):
        return (
            "成交流程",
            "POST",
            "/api/operator/trade/orders/{orderNo}/simulate-match|fill-reports",
            "X-Operator-Token",
            "全成/部成/幂等/费用",
            "200",
            "对账一致",
        )
    if tid.startswith("TC-RISK-"):
        return ("风控", "POST", "/api/app/trade/orders 与 /api/app/trade/risk/precheck", "无/App", "硬软规则", "200", "拦截或 violations")
    if tid.startswith("TC-QRY-"):
        return ("查询权限", "GET", "/api/app/report/* 与 /api/operator/report/*", "userId 或 Token", "越权与组合查询", "200", "隔离正确")
    return ("其他", "-", "-", "-", scene[:40], "-", "-")


def main():
    rows_out = []
    with SRC.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        for row in reader:
            if not row or not row[0].strip():
                continue
            tid = row[0].strip()
            scene = row[1].strip() if len(row) > 1 else ""
            steps = row[2].strip() if len(row) > 2 else ""
            expected = row[3].strip() if len(row) > 3 else ""
            mod, http, path, auth, keys, eh, ec = api_row(tid, scene)
            pr = priority_for(tid)
            rows_out.append(
                [
                    tid,
                    pr,
                    mod,
                    http,
                    path,
                    auth,
                    keys,
                    eh,
                    ec,
                    expected[:200] + ("…" if len(expected) > 200 else ""),
                    steps[:120] + ("…" if len(steps) > 120 else ""),
                ]
            )

    out_header = [
        "用例ID",
        "优先级",
        "模块",
        "HTTP",
        "路径",
        "鉴权",
        "关键入参",
        "预期HTTP",
        "预期业务code说明",
        "预期结果摘要",
        "预置与步骤摘要",
    ]
    with OUT.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(out_header)
        w.writerows(rows_out)
    print(f"Wrote {len(rows_out)} rows to {OUT}")


if __name__ == "__main__":
    main()
