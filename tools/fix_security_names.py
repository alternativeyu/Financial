#!/usr/bin/env python3
"""
Patch md_security.security_name for known A-share symbols.

Use this script when historical sync wrote code as security_name.
"""

from __future__ import annotations

import argparse
import os

import pymysql


DEFAULT_SECURITIES = [
    {"ts_code": "600000.SH", "name": "浦发银行"},
    {"ts_code": "600036.SH", "name": "招商银行"},
    {"ts_code": "600519.SH", "name": "贵州茅台"},
    {"ts_code": "601318.SH", "name": "中国平安"},
    {"ts_code": "601398.SH", "name": "工商银行"},
    {"ts_code": "000001.SZ", "name": "平安银行"},
    {"ts_code": "000333.SZ", "name": "美的集团"},
    {"ts_code": "000651.SZ", "name": "格力电器"},
    {"ts_code": "002594.SZ", "name": "比亚迪"},
    {"ts_code": "300750.SZ", "name": "宁德时代"},
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fix md_security names by built-in mapping.")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", "20050226"))
    parser.add_argument("--database", default=os.getenv("DB_NAME", "financial_trading"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    conn = pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
        cursorclass=pymysql.cursors.DictCursor,
    )

    updated = 0
    try:
        with conn.cursor() as cur:
            for item in DEFAULT_SECURITIES:
                symbol, suffix = item["ts_code"].split(".")
                market_code = "1" if suffix.upper() == "SH" else "0"
                new_name = item["name"]
                cur.execute(
                    """
                    UPDATE md_security
                    SET security_name = %s,
                        updated_at = NOW()
                    WHERE market_code = %s
                      AND security_code = %s
                      AND (security_name = security_code OR security_name IS NULL OR security_name = '')
                    """,
                    (new_name, market_code, symbol),
                )
                updated += cur.rowcount
        conn.commit()
        print(f"Patched security_name rows: {updated}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
