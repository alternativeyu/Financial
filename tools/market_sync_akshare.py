#!/usr/bin/env python3
"""
Development market data sync:
AkShare -> md_security + md_market_quote
"""

from __future__ import annotations

import argparse
import datetime as dt
import math
import os
import time
from decimal import Decimal, ROUND_HALF_UP

import akshare as ak
import pymysql


def d4(value: float) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)


def market_by_code(code: str) -> str:
    return "SH" if code.startswith("6") else "SZ"


def next_id(seed: int, index: int) -> int:
    return seed + index


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync A-share market data to MySQL.")
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", ""))
    parser.add_argument("--database", default=os.getenv("DB_NAME", "financial_trading"))
    parser.add_argument("--limit", type=int, default=300, help="Number of symbols to sync.")
    parser.add_argument(
        "--use-system-proxy",
        action="store_true",
        help="Use system proxy settings. Default is disabled for stability.",
    )
    return parser.parse_args()


def disable_proxy_env() -> None:
    # requests/urllib3 honor these env vars by default.
    os.environ["HTTP_PROXY"] = ""
    os.environ["HTTPS_PROXY"] = ""
    os.environ["ALL_PROXY"] = ""
    os.environ["http_proxy"] = ""
    os.environ["https_proxy"] = ""
    os.environ["all_proxy"] = ""
    os.environ["NO_PROXY"] = "*"
    os.environ["no_proxy"] = "*"


def main() -> None:
    args = parse_args()
    if not args.use_system_proxy:
        disable_proxy_env()
    df = ak.stock_zh_a_spot_em()
    if df is None or df.empty:
        raise RuntimeError("AkShare returned empty dataframe.")

    # Keep only reasonable rows for development.
    df = df[df["代码"].astype(str).str.len() == 6].copy()
    df = df.head(args.limit)

    now = dt.datetime.now()
    trade_date = now.date()
    seed = int(time.time() * 1000) * 1000

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

    sec_rows = []
    quote_rows = []
    for i, (_, row) in enumerate(df.iterrows()):
        code = str(row["代码"]).strip()
        name = str(row["名称"]).strip()
        market = market_by_code(code)
        latest = float(row["最新价"]) if not math.isnan(float(row["最新价"])) else 0.0
        prev_close = float(row["昨收"]) if not math.isnan(float(row["昨收"])) else latest
        upper = prev_close * 1.1
        lower = prev_close * 0.9
        volume = int(float(row["成交量"])) if str(row["成交量"]) != "nan" else 0
        amount = float(row["成交额"]) if str(row["成交额"]) != "nan" else 0.0

        sec_rows.append(
            {
                "id": next_id(seed, i),
                "market_code": "1" if market == "SH" else "0",
                "security_code": code,
                "security_name": name,
                "security_type": "EQUITY",
                "listed_status": "1",
            }
        )
        quote_rows.append(
            {
                "current_price": d4(latest),
                "prev_close_price": d4(prev_close),
                "upper_limit_price": d4(upper),
                "lower_limit_price": d4(lower),
                "volume": volume,
                "amount": d4(amount),
                "market_code": "1" if market == "SH" else "0",
                "security_code": code,
            }
        )

    try:
        with conn.cursor() as cur:
            for sec in sec_rows:
                cur.execute(
                    """
                    INSERT INTO md_security
                    (id, market_code, security_code, security_name, security_type, currency_code,
                     lot_size, price_tick, listed_status, source_stamp_tax_rate, created_at, updated_at)
                    VALUES
                    (%s, %s, %s, %s, %s, '0', 100, 0.0100, %s, NULL, NOW(), NOW())
                    ON DUPLICATE KEY UPDATE
                      security_name = VALUES(security_name),
                      security_type = VALUES(security_type),
                      listed_status = VALUES(listed_status),
                      updated_at = NOW()
                    """,
                    (
                        sec["id"],
                        sec["market_code"],
                        sec["security_code"],
                        sec["security_name"],
                        sec["security_type"],
                        sec["listed_status"],
                    ),
                )

            # refresh security id map
            code_pairs = [(s["market_code"], s["security_code"]) for s in sec_rows]
            id_map = {}
            for market_code, security_code in code_pairs:
                cur.execute(
                    "SELECT id FROM md_security WHERE market_code = %s AND security_code = %s LIMIT 1",
                    (market_code, security_code),
                )
                row = cur.fetchone()
                if row:
                    id_map[(market_code, security_code)] = row["id"]

            for i, q in enumerate(quote_rows):
                sec_id = id_map.get((q["market_code"], q["security_code"]))
                if sec_id is None:
                    continue
                cur.execute(
                    """
                    INSERT INTO md_market_quote
                    (id, security_id, trade_date, quote_time, current_price, prev_close_price,
                     upper_limit_price, lower_limit_price, volume, amount, created_at)
                    VALUES
                    (%s, %s, %s, NOW(), %s, %s, %s, %s, %s, %s, NOW())
                    ON DUPLICATE KEY UPDATE
                      current_price = VALUES(current_price),
                      prev_close_price = VALUES(prev_close_price),
                      upper_limit_price = VALUES(upper_limit_price),
                      lower_limit_price = VALUES(lower_limit_price),
                      volume = VALUES(volume),
                      amount = VALUES(amount)
                    """,
                    (
                        next_id(seed + 500000, i),
                        sec_id,
                        trade_date,
                        q["current_price"],
                        q["prev_close_price"],
                        q["upper_limit_price"],
                        q["lower_limit_price"],
                        q["volume"],
                        q["amount"],
                    ),
                )

        conn.commit()
        print(f"Synced {len(sec_rows)} securities and {len(quote_rows)} quotes.")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
