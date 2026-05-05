-- 佣金/印花税规则种子（与 TradingFeeRuleService 默认回退值一致，便于走规则表路径）
-- 执行前请确认 md_commission_rule / md_tax_rule 已建表（见 mysql_financial_trading_schema.sql）

INSERT INTO md_commission_rule (
  id, acct_cls_code, market_code, security_type, commission_rate, min_fee, max_fee, effective_date, expire_date, status
) VALUES
  (920001, 'NORMAL', '1', 'COMMON', 0.000250, 0.0100, NULL, '2020-01-01', NULL, '1'),
  (920002, 'NORMAL', '0', 'COMMON', 0.000250, 0.0100, NULL, '2020-01-01', NULL, '1'),
  (920003, 'VIP', '1', 'COMMON', 0.000200, 0.0100, NULL, '2020-01-01', NULL, '1'),
  (920004, 'VIP', '0', 'COMMON', 0.000200, 0.0100, NULL, '2020-01-01', NULL, '1')
ON DUPLICATE KEY UPDATE
  commission_rate = VALUES(commission_rate),
  min_fee = VALUES(min_fee),
  max_fee = VALUES(max_fee),
  status = VALUES(status);

-- 卖出印花税（买入方向若无记录则税率为 0）
INSERT INTO md_tax_rule (
  id, fee_type, market_code, security_type, trade_direction, tax_rate, calc_mode, min_fee, effective_date, expire_date, status
) VALUES
  (920101, 'STAMP', '1', 'COMMON', 'S', 0.001000, 'RATE', 0.0000, '2020-01-01', NULL, '1'),
  (920102, 'STAMP', '0', 'COMMON', 'S', 0.001000, 'RATE', 0.0000, '2020-01-01', NULL, '1')
ON DUPLICATE KEY UPDATE
  tax_rate = VALUES(tax_rate),
  status = VALUES(status);
