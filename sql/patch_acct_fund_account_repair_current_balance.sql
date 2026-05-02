-- 修复历史数据：current_balance 应与 可用+冻结 一致（旧版下单/撤单未同步 current_balance 时会产生偏差）
UPDATE acct_fund_account
SET current_balance = available_balance + frozen_balance,
    updated_at = NOW()
WHERE ABS(IFNULL(current_balance, 0) - (IFNULL(available_balance, 0) + IFNULL(frozen_balance, 0))) > 0.0001;
