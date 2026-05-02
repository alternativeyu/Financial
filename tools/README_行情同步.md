# 开发联调行情同步（AkShare）

用于本地开发快速写入：

- `md_security`（证券主数据）
- `md_market_quote`（行情快照）

## 1. 安装依赖

```bash
cd tools
pip install -r requirements.txt
```

## 2. 执行同步

```bash
python market_sync_akshare.py --host 127.0.0.1 --port 3306 --user root --password 你的密码 --database financial_trading --limit 300
```

也可用环境变量：

```bash
set DB_HOST=127.0.0.1
set DB_PORT=3306
set DB_USER=root
set DB_PASSWORD=你的密码
set DB_NAME=financial_trading
python market_sync_akshare.py --limit 300
```

## 3. 说明

- 市场映射：`6xxxxx -> SH(1)`，其他A股代码 -> `SZ(0)`
- 涨跌停价：开发环境按昨收 * 1.1 / 0.9 计算（便于风控联调）
- 该脚本用于开发联调，不建议直接用于生产行情链路
- 若历史数据中 `security_name` 被写成代码，可执行 `fix_security_names.py` 进行补丁修正（仅修正“名称等于代码”的记录）

## 4. Tushare 方案（推荐）

当 AkShare 网络不稳定时，改用 Tushare：

```bash
pip install -r requirements.txt
python market_sync_tushare.py --token 你的TushareToken --host 127.0.0.1 --port 3306 --user root --password 你的密码 --database financial_trading --limit 300
```

也可环境变量方式：

```bash
set TUSHARE_TOKEN=你的TushareToken
python market_sync_tushare.py --host 127.0.0.1 --port 3306 --user root --password 你的密码 --database financial_trading --limit 300
```

当前脚本已适配低权限：默认不调用 `stock_basic`，而是使用内置代码列表通过 `daily` 拉取。  
内置列表包含真实证券名称（如“浦发银行/宁德时代”），会写入 `md_security.security_name`。  
可手动传入代码列表：

```bash
python market_sync_tushare.py --token 你的TushareToken --symbols 600000.SH,000001.SZ,300750.SZ --host 127.0.0.1 --port 3306 --user root --password 你的密码 --database financial_trading
```

说明：若使用 `--symbols` 自定义代码，脚本在无名称映射时会回退为“代码作为名称”。如需真实名称，建议扩展脚本内 `DEFAULT_SECURITIES` 映射或接入可用的基础信息接口。

## 5. 名称补丁修正（不重跑行情）

当行情页面出现“名称=代码”时，可执行：

```bash
python fix_security_names.py --host 127.0.0.1 --port 3306 --user root --password 你的密码 --database financial_trading
```

该脚本只会更新 `md_security` 中以下情况的记录：

- `security_name = security_code`
- `security_name IS NULL`
- `security_name = ''`
