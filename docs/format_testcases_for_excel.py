# -*- coding: utf-8 -*-
"""
Read 系统功能测试用例_模板对齐.csv and emit:
1) 系统功能测试用例_表格式.csv — 9 列与 QC 模板对齐，测试步骤内「预置条件 / 操作步骤」分段换行，预期结果分行编号。
2) 系统功能测试用例_表格式.html — 浏览器打开后全选复制到 Excel，保留表头与换行。
"""
import csv
import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "系统功能测试用例_模板对齐.csv"
OUT_CSV = ROOT / "系统功能测试用例_表格式.csv"
OUT_HTML = ROOT / "系统功能测试用例_表格式.html"


def split_pre_op(steps: str) -> tuple[str, str]:
    s = (steps or "").strip()
    if not s:
        return "", ""
    # 常见写法：预置条件：... 操作步骤：...
    m = re.match(
        r"^\s*预置条件[：:]\s*(.+?)\s*操作步骤[：:]\s*(.+)\s*$",
        s,
        re.DOTALL,
    )
    if m:
        return m.group(1).strip(), m.group(2).strip()
    return s, ""


def format_steps_block(pre: str, op: str) -> str:
    lines = ["预置条件：", f"1. {pre}" if pre else "1. （无）"]
    lines.append("")
    lines.append("操作步骤：")
    if op:
        lines.append(f"1. {op}")
    else:
        lines.append("1. （见场景说明与接口文档）")
    return "\n".join(lines)


def format_expected_block(expected: str) -> str:
    e = (expected or "").strip()
    if not e:
        return ""
    # 将「1) 2) 3)」转为「1. …\n2. …」便于 Excel 单元格阅读
    if re.search(r"\d+\)", e):
        t = re.sub(r"\s*(\d+)\)\s*", r"\n\1. ", e).strip()
        return t
    return f"1. {e}"


def main():
    rows = []
    with SRC.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        for row in reader:
            if not row or not str(row[0]).strip():
                continue
            while len(row) < 9:
                row.append("")
            rows.append(row[:9])

    # 表头与模板：A 测试功能点 B 子功能检查点 C 测试步骤 D 预期结果 E-I
    out_header = [
        "测试功能点",
        "子功能检查点",
        "测试步骤",
        "预期结果",
        "执行状态",
        "实际结果",
        "作者或修改者",
        "最后执行者",
        "最后执行日期",
    ]

    formatted = []
    for row in rows:
        tid, scene, steps, exp = row[0], row[1], row[2], row[3]
        pre, op = split_pre_op(steps)
        steps_fmt = format_steps_block(pre, op)
        exp_fmt = format_expected_block(exp)
        formatted.append(
            [
                tid.strip(),
                scene.strip(),
                steps_fmt,
                exp_fmt,
                row[4].strip(),
                row[5].strip(),
                row[6].strip() or "测试设计",
                row[7].strip(),
                row[8].strip(),
            ]
        )

    with OUT_CSV.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_MINIMAL)
        w.writerow(out_header)
        for r in formatted:
            w.writerow(r)

    # HTML 表（便于复制到 Excel）
    th_style = (
        "background:#c6efce;color:#c00000;font-weight:bold;"
        "border:1px solid #375623;padding:8px;text-align:center;"
    )
    td_style = "border:1px solid #9bbb59;padding:6px;vertical-align:top;white-space:pre-wrap;"
    td_b_style = td_style + "background:#fde9d9;"  # B 列浅橙
    td_c_style = td_style + "background:#e2efda;"  # C 预置/步骤
    td_d_style = td_style + "background:#e2efda;"
    td_ghi_style = td_style + "background:#e2efda;"

    parts = [
        "<!DOCTYPE html><html><head><meta charset='utf-8'/>",
        "<title>系统功能测试用例（表格式）</title>",
        "<style>body{font-family:Microsoft YaHei,Arial,sans-serif;font-size:12px;} table{border-collapse:collapse;} caption{padding:8px;font-weight:bold;}</style>",
        "</head><body>",
        "<table>",
        "<caption>系统功能测试用例 — 全选复制到 Excel（9 列）</caption>",
        "<thead><tr>",
    ]
    for i, h in enumerate(out_header):
        parts.append(f"<th style='{th_style}'>{html.escape(h)}</th>")
    parts.append("</tr></thead><tbody>")

    for r in formatted:
        parts.append("<tr>")
        cells = [
            (r[0], td_style),
            (r[1], td_b_style),
            (r[2], td_c_style),
            (r[3], td_d_style),
            (r[4], td_style),
            (r[5], td_style),
            (r[6], td_ghi_style),
            (r[7], td_ghi_style),
            (r[8], td_ghi_style),
        ]
        for text, st in cells:
            parts.append(f"<td style='{st}'>{html.escape(text)}</td>")
        parts.append("</tr>")
    parts.append("</tbody></table>")
    parts.append("<p style='color:#666'>说明：从表头行开始全选复制，粘贴到 Excel 为 9 列；单元格内换行在 Excel 中 Alt+Enter 可见。</p>")
    parts.append("</body></html>")
    OUT_HTML.write_text("".join(parts), encoding="utf-8")

    print(f"Wrote {len(formatted)} rows to {OUT_CSV.name} and {OUT_HTML.name}")


if __name__ == "__main__":
    main()
