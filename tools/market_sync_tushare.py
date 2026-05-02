#!/usr/bin/env python3
"""
Development market data sync:
Tushare -> md_security + md_market_quote
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import time
from decimal import Decimal, ROUND_HALF_UP

import pymysql
import tushare as ts

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


def d4(value) -> Decimal:
    return Decimal(str(value)).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)


def next_id(seed: int, index: int) -> int:
    return seed + index


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync A-share market data by Tushare.")
    parser.add_argument("--token", default=os.getenv("TUSHARE_TOKEN", ""))
    parser.add_argument("--host", default=os.getenv("DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--user", default=os.getenv("DB_USER", "root"))
    parser.add_argument("--password", default=os.getenv("DB_PASSWORD", ""))
    parser.add_argument("--database", default=os.getenv("DB_NAME", "financial_trading"))
    parser.add_argument("--limit", type=int, default=300, help="Number of symbols to sync.")
    parser.add_argument(
        "--symbols",
        default="",
        help="Comma separated ts_code list, e.g. 600000.SH,000001.SZ",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.token:
        raise RuntimeError("Missing Tushare token. Use --token or TUSHARE_TOKEN env.")

    pro = ts.pro_api(args.token)

    if args.symbols.strip():
        ts_codes = [s.strip().upper() for s in args.symbols.split(",") if s.strip()]
        name_map = {}
    else:
        default_list = DEFAULT_SECURITIES[: min(len(DEFAULT_SECURITIES), args.limit)]
        ts_codes = [item["ts_code"] for item in default_list]
        name_map = {item["ts_code"]: item["name"] for item in default_list}
    if not ts_codes:
        raise RuntimeError("No symbols configured. Use --symbols.")

    trade_date = dt.datetime.now().strftime("%Y%m%d")
    quote_map = {}
    for ts_code in ts_codes:
        q = pro.daily(ts_code=ts_code, trade_date=trade_date, fields="ts_code,trade_date,close,pre_close,vol,amount")
        if q is None or q.empty:
            # fallback to recent days
            for i in range(1, 8):
                prev_day = (dt.date.today() - dt.timedelta(days=i)).strftime("%Y%m%d")
                q = pro.daily(ts_code=ts_code, trade_date=prev_day, fields="ts_code,trade_date,close,pre_close,vol,amount")
                if q is not None and not q.empty:
                    break
        if q is None or q.empty:
            continue
        quote_map[ts_code] = q.iloc[0]

    if not quote_map:
        raise RuntimeError("No quotes fetched by daily interface for configured symbols.")

    now = dt.datetime.now()
    seed = int(time.time() * 1000) * 1000

    sec_rows = []
    quote_rows = []
    for i, ts_code in enumerate(ts_codes):
        symbol = ts_code.split(".")[0]
        name = name_map.get(ts_code, symbol)
        suffix = ts_code.split(".")[-1].upper()
        market_code = "1" if suffix == "SH" else "0"
        sec_rows.append(
            {
                "id": next_id(seed, i),
                "market_code": market_code,
                "security_code": symbol,
                "security_name": name,
                "security_type": "EQUITY",
                "listed_status": "1",
                "ts_code": ts_code,
            }
        )

        q = quote_map.get(ts_code)
        if q is None:
            continue
        close = d4(q["close"])
        prev_close = d4(q["pre_close"])
        upper = d4(prev_close * Decimal("1.1"))
        lower = d4(prev_close * Decimal("0.9"))
        volume = int(float(q["vol"]) * 100) if q["vol"] is not None else 0
        amount = d4(float(q["amount"]) * 1000) if q["amount"] is not None else Decimal("0.0000")
        quote_rows.append(
            {
                "market_code": market_code,
                "security_code": symbol,
                "current_price": close,
                "prev_close_price": prev_close,
                "upper_limit_price": upper,
                "lower_limit_price": lower,
                "volume": volume,
                "amount": amount,
                "trade_date": dt.datetime.strptime(str(q["trade_date"]), "%Y%m%d").date(),
            }
        )

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

            id_map = {}
            for sec in sec_rows:
                cur.execute(
                    "SELECT id FROM md_security WHERE market_code = %s AND security_code = %s LIMIT 1",
                    (sec["market_code"], sec["security_code"]),
                )
                found = cur.fetchone()
                if found:
                    id_map[(sec["market_code"], sec["security_code"])] = found["id"]

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
                        q["trade_date"],
                        q["current_price"],
                        q["prev_close_price"],
                        q["upper_limit_price"],
                        q["lower_limit_price"],
                        q["volume"],
                        q["amount"],
                    ),
                )

        conn.commit()
        print(f"Tushare synced {len(sec_rows)} securities, {len(quote_rows)} quotes.")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    main()
