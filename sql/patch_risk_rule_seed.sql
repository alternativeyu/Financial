-- 风控规则初始化数据（R001-R010）
-- 执行前请确保 risk_rule 表已存在（见主 schema 脚本）

INSERT IGNORE INTO risk_rule
  (id, rule_code, rule_name, rule_stage, rule_type, param_json, severity, block_flag, status, effective_date, remark)
VALUES
  (1001,'R001','客户状态校验',       'PRE_ORDER',    'IDENTITY_CHECK',  NULL,                        'HIGH',   1,'1','2024-01-01','客户状态非NORMAL则阻断'),
  (1002,'R002','资金账户状态校验',   'PRE_ORDER',    'ACCOUNT_CHECK',   NULL,                        'HIGH',   1,'1','2024-01-01','资金账户状态非NORMAL则阻断'),
  (1003,'R003','股东账户状态校验',   'PRE_ORDER',    'ACCOUNT_CHECK',   NULL,                        'HIGH',   1,'1','2024-01-01','证券账户状态异常则阻断'),
  (1004,'R004','证券可交易状态校验', 'PRE_ORDER',    'SECURITY_CHECK',  NULL,                        'MEDIUM', 1,'1','2024-01-01','未上市/停牌/不可交易则阻断'),
  (1005,'R005','委托数量交易单位校验','PRE_ORDER',   'ORDER_CHECK',     NULL,                        'LOW',    1,'1','2024-01-01','数量不是lot_size整数倍则阻断'),
  (1006,'R006','涨跌停价格校验',     'PRE_ORDER',    'PRICE_CHECK',     NULL,                        'MEDIUM', 1,'1','2024-01-01','买入超涨停/卖出低跌停则阻断'),
  (1007,'R007','买入资金充足性校验', 'PRE_ORDER',    'FUND_CHECK',      NULL,                        'HIGH',   1,'1','2024-01-01','可用资金不足(含预估佣金税费)则阻断'),
  (1008,'R008','卖出持仓充足性校验', 'PRE_ORDER',    'POSITION_CHECK',  NULL,                        'HIGH',   1,'1','2024-01-01','可卖持仓不足则阻断'),
  (1009,'R009','单笔委托金额上限',   'PRE_ORDER',    'AMOUNT_CHECK',    '{"maxAmount":5000000}',     'MEDIUM', 0,'1','2024-01-01','超过单笔金额上限则预警(不阻断)'),
  (1010,'R010','高频撤单监控',       'CANCEL_ORDER', 'BEHAVIOR_MONITOR','{"maxCancelPerDay":10}',    'MEDIUM', 0,'1','2024-01-01','当日撤单次数超阈值则预警(不阻断)');
