-- ============================================================
-- 测试数据种子脚本
-- 执行前提：主 schema + patch_risk_rule_seed + patch_md_trading_fee_rules_seed 已执行
-- 执行方式：mysql -u root -p financial_trading < sql/seed_test_data.sql
-- ============================================================

USE financial_trading;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 证券主数据（6 只股票：3 沪A + 2 深A + 1 已停牌用于触发R004）
-- ============================================================
INSERT IGNORE INTO md_security
  (id, market_code, security_code, security_name, security_type, currency_code, lot_size, price_tick, listed_status)
VALUES
  (100001, '1', '600000', '浦发银行',   'COMMON', '0', 100, 0.01, '1'),
  (100002, '1', '600519', '贵州茅台',   'COMMON', '0', 100, 0.01, '1'),
  (100003, '1', '601318', '中国平安',   'COMMON', '0', 100, 0.01, '1'),
  (100004, '0', '000001', '平安银行',   'COMMON', '0', 100, 0.01, '1'),
  (100005, '0', '000858', '五粮液',     'COMMON', '0', 100, 0.01, '1'),
  -- 停牌股票：用于触发 R004（证券不可交易阻断）
  (100006, '1', '600001', '测试停牌股', 'COMMON', '0', 100, 0.01, '0');

-- ============================================================
-- 2. 行情快照（5 只正常股票的今日行情，用于行情中心展示 & 风控价格校验）
-- ============================================================
INSERT IGNORE INTO md_market_quote
  (id, security_id, trade_date, quote_time, current_price, prev_close_price, upper_limit_price, lower_limit_price, volume, amount)
VALUES
  (200001, 100001, CURDATE(), NOW(), 9.85,    9.80,  10.78,  8.82,  35820000, 353000000.00),
  (200002, 100002, CURDATE(), NOW(), 1728.00, 1710.00, 1881.00, 1539.00, 890000, 1530000000.00),
  (200003, 100003, CURDATE(), NOW(), 42.50,   42.10,  46.31,  37.89, 12300000, 522750000.00),
  (200004, 100004, CURDATE(), NOW(), 11.20,   11.10,  12.21,  9.99,  28500000, 319200000.00),
  (200005, 100005, CURDATE(), NOW(), 158.60,  157.00, 172.70, 141.30, 4200000, 666120000.00);

-- ============================================================
-- 3. 客户主数据
--    C001 NORMAL    - 正常客户，可正常下单
--    C002 NORMAL    - 正常客户（资金少），可触发 R007 余额不足
--    C003 FROZEN    - 冻结客户，下单触发 R001
-- ============================================================
INSERT IGNORE INTO cust_customer
  (id, customer_code, customer_name, id_type, id_no, customer_type, acct_cls_code, customer_status, risk_level, open_date, mobile, created_by)
VALUES
  (300001, 'C001', '张三', 'ID_CARD', '110101199001011234', 'INDIVIDUAL', 'NORMAL', 'NORMAL', 'R3', '2024-01-10', '13800001001', 900001),
  (300002, 'C002', '李四', 'ID_CARD', '110101199002022345', 'INDIVIDUAL', 'NORMAL', 'NORMAL', 'R2', '2024-02-15', '13800001002', 900001),
  (300003, 'C003', '王五（冻结）', 'ID_CARD', '110101199003033456', 'INDIVIDUAL', 'NORMAL', 'FROZEN', 'R1', '2024-03-20', '13800001003', 900001);

-- ============================================================
-- 4. 资金账户
--    FA001 充裕资金（50万）
--    FA002 少量资金（5000），用于触发 R007
--    FA003 冻结账户，用于触发 R002
-- ============================================================
INSERT IGNORE INTO acct_fund_account
  (id, fund_account_no, customer_id, currency_code, current_balance, available_balance, frozen_balance, status)
VALUES
  (400001, 'FA001', 300001, '0', 500000.0000, 500000.0000, 0.0000, 'NORMAL'),
  (400002, 'FA002', 300002, '0', 5000.0000,   5000.0000,   0.0000, 'NORMAL'),
  (400003, 'FA003', 300003, '0', 100000.0000, 100000.0000, 0.0000, 'FROZEN');

-- ============================================================
-- 5. 股东账户（每位客户分配沪A + 深A）
-- ============================================================
INSERT IGNORE INTO acct_shareholder_account
  (id, customer_id, market_code, shareholder_account_no, account_status)
VALUES
  (500001, 300001, '1', 'SH1001001001', 'NORMAL'),
  (500002, 300001, '0', 'SZ1001001001', 'NORMAL'),
  (500003, 300002, '1', 'SH1001002001', 'NORMAL'),
  (500004, 300002, '0', 'SZ1001002001', 'NORMAL'),
  (500005, 300003, '1', 'SH1001003001', 'NORMAL'),
  (500006, 300003, '0', 'SZ1001003001', 'NORMAL');

-- ============================================================
-- 6. 持仓
--    C001 持有 600000×1000、601318×500
--    C002 持有 000001×200（可用100，冻结100，用于触发 R008 卖出不足）
-- ============================================================
INSERT IGNORE INTO acct_position
  (id, fund_account_id, customer_id, security_id, market_code, security_code_snapshot,
   total_qty, available_qty, frozen_qty, cost_price, last_price, market_value, position_status)
VALUES
  (600001, 400001, 300001, 100001, '1', '600000', 1000, 1000, 0,  9.50,  9.85,  9850.0000,   'NORMAL'),
  (600002, 400001, 300001, 100003, '1', '601318', 500,  500,  0,  40.00, 42.50, 21250.0000,  'NORMAL'),
  (600003, 400002, 300002, 100004, '0', '000001', 200,  100,  100, 10.80, 11.20, 2240.0000,  'NORMAL');

-- ============================================================
-- 7. 开户审核：手动导入批次 + 申请记录（供"开户审核"页面展示）
--    记录1: SUBMITTED 待接单
--    记录2: IN_REVIEW 审核中
--    记录3: OPENED    已开户（关联 C001）
-- ============================================================
INSERT IGNORE INTO cust_import_batch
  (id, batch_no, import_mode, source_file_name, total_count, pass_count, reject_count, batch_status, imported_by, imported_at)
VALUES
  (880001, 'BATCH20240110001', 'MANUAL', 'test_import.csv', 3, 3, 0, 'DONE', 900001, '2024-01-10 09:00:00');

INSERT IGNORE INTO cust_import_record
  (id, batch_id, record_no, row_no, customer_name, id_type, id_no, acct_cls_code,
   sh_shareholder_acct, sz_shareholder_acct, import_status, validate_message, review_status, created_customer_id)
VALUES
  (890001, 880001, 'REC20240110001', 1, '赵六',   'ID_CARD', '110101199004044567', 'NORMAL',
   'SH1001004001', 'SZ1001004001', 'PASS', NULL, 'SUBMITTED',  NULL),
  (890002, 880001, 'REC20240110002', 2, '孙七',   'ID_CARD', '110101199005055678', 'VIP',
   'SH1001005001', 'SZ1001005001', 'PASS', NULL, 'IN_REVIEW',  NULL),
  (890003, 880001, 'REC20240110003', 3, '张三',   'ID_CARD', '110101199001011234', 'NORMAL',
   'SH1001001001', 'SZ1001001001', 'PASS', NULL, 'OPENED',     300001);

-- 审核日志（记录3的开户轨迹）
INSERT IGNORE INTO cust_open_audit_log
  (id, record_id, audit_no, audit_result, audit_comment, auditor_id, previous_status, current_status, audited_at)
VALUES
  (891001, 890003, 'AUD20240110001', 'CLAIM',  '操作员接单',           900001, 'SUBMITTED',  'IN_REVIEW', '2024-01-10 09:10:00'),
  (891002, 890003, 'AUD20240110002', 'APPROVE','资料齐全，审核通过',    900001, 'IN_REVIEW',  'APPROVED',  '2024-01-10 09:30:00'),
  (891003, 890003, 'AUD20240110003', 'OPEN_ACCOUNT','生成客户与账户',  900001, 'APPROVED',   'OPENED',    '2024-01-10 09:31:00');

-- ============================================================
-- 8. App 用户 + 开户申请（供"一键导入App申请"功能展示）
-- ============================================================
INSERT IGNORE INTO app_user
  (id, username, password_hash, mobile, status)
VALUES
  (860001, 'appuser01', 'e10adc3949ba59abbe56e057f20f883e', '13900001001', 'ACTIVE'),
  (860002, 'appuser02', 'e10adc3949ba59abbe56e057f20f883e', '13900001002', 'ACTIVE');

INSERT IGNORE INTO cust_open_apply
  (id, apply_no, user_id, customer_name, id_type, id_no, mobile, acct_cls_code,
   shareholder_market, sh_account, sz_account, apply_status, audit_comment)
VALUES
  (870001, 'APP20240501001', 860001, '陈八', 'ID_CARD', '110101199006066789', '13900001001',
   'NORMAL', 'SH', 'SH1001006001', 'SZ1001006001', 'WAIT_AUDIT', NULL),
  (870002, 'APP20240501002', 860002, '周九', 'ID_CARD', '110101199007077890', '13900001002',
   'VIP',    'SH', 'SH1001007001', 'SZ1001007001', 'WAIT_AUDIT', NULL);

-- ============================================================
-- 9. 委托记录（10 笔，涵盖各种状态）
--    注意：INIT=待报, REPORTED=已报, PART_FILLED=部成, FILLED=全成, CANCELED=已撤, REJECTED=废单
-- ============================================================
INSERT IGNORE INTO trd_order
  (id, order_no, customer_id, fund_account_id, shareholder_account_id, security_id,
   market_code, security_code, security_name_snapshot, trade_direction,
   order_price, order_qty, order_amount, filled_qty, filled_amount, avg_fill_price,
   frozen_cash, frozen_qty, order_status, risk_check_result, dispatch_status, source_type, created_by, created_at)
VALUES
  -- 进行中委托
  (700001,'ORD20240501001',300001,400001,500001,100001,'1','600000','浦发银行','B',
   9.80, 500, 4900.0000,   0,   0.0000, 0.0000, 4901.2250, 500, 'REPORTED',    'PASS','SENT',   'OPERATOR',900001,'2024-05-01 09:31:00'),
  (700002,'ORD20240501002',300001,400001,500001,100002,'1','600519','贵州茅台','B',
   1720.00,100,172000.0000, 50, 86000.0000,1720.00,172000.0000,50,'PART_FILLED','PASS','SENT',  'OPERATOR',900001,'2024-05-01 09:35:00'),
  (700003,'ORD20240501003',300001,400001,500001,100003,'1','601318','中国平安','S',
   42.00, 200, 8400.0000,   0,   0.0000, 0.0000, 0.0000,    0,  'REPORTED',    'PASS','SENT',   'OPERATOR',900001,'2024-05-01 09:40:00'),
  -- 已完成委托
  (700004,'ORD20240501004',300001,400001,500001,100001,'1','600000','浦发银行','B',
   9.75, 1000, 9750.0000, 1000, 9750.0000, 9.75, 0.0000, 0, 'FILLED',    'PASS','REPLIED', 'OPERATOR',900001,'2024-05-01 09:20:00'),
  (700005,'ORD20240501005',300001,400001,500001,100003,'1','601318','中国平安','S',
   41.50, 300, 12450.0000, 300, 12450.0000, 41.50, 0.0000, 0, 'FILLED',   'PASS','REPLIED', 'APP',     900001,'2024-05-01 09:22:00'),
  -- 已撤单委托
  (700006,'ORD20240501006',300001,400001,500002,100004,'0','000001','平安银行','B',
   11.00, 500, 5500.0000,   0,   0.0000, 0.0000, 0.0000, 0, 'CANCELED',  'PASS','REPLIED', 'OPERATOR',900001,'2024-05-01 09:25:00'),
  (700007,'ORD20240501007',300001,400001,500002,100005,'0','000858','五粮液','B',
   155.00,200,31000.0000,  100, 15500.0000,155.00,0.0000, 0, 'PART_CANCELED','PASS','REPLIED','APP',900001,'2024-05-01 09:27:00'),
  -- C002客户委托
  (700008,'ORD20240501008',300002,400002,500003,100001,'1','600000','浦发银行','B',
   9.90, 100, 990.0000,   100, 990.0000, 9.90, 0.0000, 0, 'FILLED',    'PASS','REPLIED', 'APP',     900001,'2024-05-02 10:00:00'),
  -- 废单（风控拦截后 REJECTED）
  (700009,'ORD20240501009',300002,400002,500003,100002,'1','600519','贵州茅台','B',
   1728.00,100,172800.0000, 0,  0.0000, 0.0000, 0.0000, 0, 'REJECTED',  'REJECTED','WAIT','OPERATOR',900001,'2024-05-02 10:05:00'),
  -- 今日新增进行中
  (700010,'ORD20240507001',300001,400001,500001,100001,'1','600000','浦发银行','B',
   9.85, 200, 1970.0000,   0,   0.0000, 0.0000, 1970.4900, 200, 'REPORTED', 'PASS','SENT',  'OPERATOR',900001,NOW());

-- ============================================================
-- 10. 成交记录（对应已成交委托）
-- ============================================================
INSERT IGNORE INTO trd_trade
  (id, trade_no, order_id, order_no, customer_id, fund_account_id, security_id,
   market_code, security_code, trade_direction, trade_price, trade_qty, trade_amount,
   commission_amount, tax_amount, other_fee_amount, net_settle_amount, trade_time, settle_status)
VALUES
  -- ORD004全成（买入600000×1000）
  (740001,'TRD20240501001',700004,'ORD20240501004',300001,400001,100001,
   '1','600000','B',9.75,1000,9750.0000,2.4375,0.0000,0.0000,-9752.4375,'2024-05-01 09:21:00','SETTLED'),
  -- ORD005全成（卖出601318×300）
  (740002,'TRD20240501002',700005,'ORD20240501005',300001,400001,100003,
   '1','601318','S',41.50,300,12450.0000,3.1125,12.4500,0.0000,12434.4375,'2024-05-01 09:23:00','SETTLED'),
  -- ORD002部成（买入贵州茅台×50）
  (740003,'TRD20240501003',700002,'ORD20240501002',300001,400001,100002,
   '1','600519','B',1720.00,50,86000.0000,21.5000,0.0000,0.0000,-86021.5000,'2024-05-01 09:36:00','SETTLED'),
  -- ORD007部撤（五粮液×100成交）
  (740004,'TRD20240501004',700007,'ORD20240501007',300001,400001,100005,
   '0','000858','B',155.00,100,15500.0000,3.8750,0.0000,0.0000,-15503.8750,'2024-05-01 09:28:00','SETTLED'),
  -- ORD008成交（C002买入600000×100）
  (740005,'TRD20240502001',700008,'ORD20240501008',300002,400002,100001,
   '1','600000','B',9.90,100,990.0000,0.2475,0.0000,0.0000,-990.2475,'2024-05-02 10:01:00','SETTLED');

-- ============================================================
-- 11. 费用明细
-- ============================================================
INSERT IGNORE INTO trd_fee_detail
  (id, biz_type, biz_id, order_id, trade_id, fee_type, calc_base, fee_rate, fee_amount, currency_code)
VALUES
  (750001,'TRADE',740001,700004,740001,'COMMISSION',9750.0000,0.000250,2.4375,'0'),
  (750002,'TRADE',740002,700005,740002,'COMMISSION',12450.0000,0.000250,3.1125,'0'),
  (750003,'TRADE',740002,700005,740002,'STAMP_TAX', 12450.0000,0.001000,12.4500,'0'),
  (750004,'TRADE',740003,700002,740003,'COMMISSION',86000.0000,0.000250,21.5000,'0'),
  (750005,'TRADE',740004,700007,740004,'COMMISSION',15500.0000,0.000250,3.8750,'0'),
  (750006,'TRADE',740005,700008,740005,'COMMISSION',990.0000,  0.000250,0.2475,'0');

-- ============================================================
-- 12. 资产流水（开户初始化 + 部分交易流水）
-- ============================================================
INSERT IGNORE INTO acct_asset_journal
  (id, journal_no, customer_id, fund_account_id, security_id, asset_type, change_type, direction,
   before_amount, change_amount, after_amount, ref_type, ref_id, ref_no, operator_id, occur_time, remark)
VALUES
  -- C001 开户初始化资金
  (760001,'JNL20240110001',300001,400001,NULL,'CASH','INIT_CASH','I',
   0.0000,500000.0000,500000.0000,'OPENING',890003,'REC20240110003',900001,'2024-01-10 09:31:00','开户初始化资金'),
  -- C001 买入600000冻结资金
  (760002,'JNL20240501001',300001,400001,NULL,'CASH','ORDER_FREEZE','D',
   500000.0000,9752.4375,490247.5625,'ORDER',700004,'ORD20240501004',900001,'2024-05-01 09:20:00','委托冻结资金'),
  -- C001 买入600000成交解冻并扣款
  (760003,'JNL20240501002',300001,400001,NULL,'CASH','TRADE_SETTLE','D',
   490247.5625,9752.4375,480495.1250,'TRADE',740001,'TRD20240501001',900001,'2024-05-01 09:21:00','买入结算扣款'),
  -- C001 卖出601318成交入账
  (760004,'JNL20240501003',300001,400001,NULL,'CASH','TRADE_SETTLE','I',
   480495.1250,12434.4375,492929.5625,'TRADE',740002,'TRD20240501002',900001,'2024-05-01 09:23:00','卖出结算入账'),
  -- C002 开户初始化
  (760005,'JNL20240110004',300002,400002,NULL,'CASH','INIT_CASH','I',
   0.0000,100000.0000,100000.0000,'OPENING',NULL,NULL,900001,'2024-02-15 10:00:00','开户初始化资金'),
  -- C002 买入成交扣款（演示数据，余额调整为5000的来源）
  (760006,'JNL20240502001',300002,400002,NULL,'CASH','TRADE_SETTLE','D',
   100000.0000,990.2475,99009.7525,'TRADE',740005,'TRD20240502001',900001,'2024-05-02 10:01:00','买入结算扣款');

-- ============================================================
-- 13. 操作日志
-- ============================================================
INSERT IGNORE INTO sys_operation_log
  (id, log_no, operator_id, module_name, biz_type, biz_id, biz_no, operation_type, operation_result, request_ip, operation_content, occurred_at)
VALUES
  (850001,'LOG20240501001',900001,'OPENING','CLAIM',  890003,'REC20240110003','CLAIM',      'SUCCESS','127.0.0.1','接单：张三开户申请',         '2024-01-10 09:10:00'),
  (850002,'LOG20240501002',900001,'OPENING','APPROVE',890003,'REC20240110003','APPROVE',    'SUCCESS','127.0.0.1','审核通过：张三开户申请',       '2024-01-10 09:30:00'),
  (850003,'LOG20240501003',900001,'TRADE',  'ORDER',  700004,'ORD20240501004','SUBMIT_ORDER','SUCCESS','127.0.0.1','提交委托：买入浦发银行×1000', '2024-05-01 09:20:00'),
  (850004,'LOG20240501004',900001,'TRADE',  'ORDER',  700005,'ORD20240501005','SUBMIT_ORDER','SUCCESS','127.0.0.1','提交委托：卖出中国平安×300',  '2024-05-01 09:22:00'),
  (850005,'LOG20240501005',900001,'TRADE',  'CANCEL', 700006,'ORD20240501006','CANCEL_ORDER','SUCCESS','127.0.0.1','撤单：买入平安银行委托',       '2024-05-01 09:26:00'),
  (850006,'LOG20240501006',900001,'TRADE',  'MATCH',  700004,'ORD20240501004','SIMULATE_MATCH','SUCCESS','127.0.0.1','模拟成交：浦发银行×1000@9.75','2024-05-01 09:21:00'),
  (850007,'LOG20240501007',900001,'RISK',   'HANDLE', NULL,  'EVT20240502001', 'HANDLE_RISK','SUCCESS','127.0.0.1','处理风控事件：R009 预警已确认','2024-05-02 11:00:00'),
  (850008,'LOG20240507001',900001,'TRADE',  'ORDER',  700010,'ORD20240507001','SUBMIT_ORDER','SUCCESS','127.0.0.1','提交委托：买入浦发银行×200',  NOW());

-- ============================================================
-- 14. 风控事件（手动插入，覆盖多种规则触发场景）
-- ============================================================
INSERT IGNORE INTO risk_event
  (id, event_no, rule_id, event_type, risk_level, is_blocking, customer_id, fund_account_id, order_id,
   hit_message, event_status, handled_by, handled_at, created_at)
VALUES
  -- R007 买入资金不足（阻断）- OPEN 待处理
  (800001,'EVT20240502001',1007,'FUND_CHECK','HIGH',1,300002,400002,700009,
   'R007: 买入预估费用 172821.50 > 可用余额 5000.00，资金不足',
   'OPEN',NULL,NULL,'2024-05-02 10:05:00'),
  -- R009 单笔委托金额预警（非阻断）- OPEN 待处理
  (800002,'EVT20240503001',1009,'AMOUNT_CHECK','MEDIUM',0,300001,400001,700002,
   'R009: 单笔委托金额 172000.00 超过上限 50000.00（预警）',
   'OPEN',NULL,NULL,'2024-05-03 09:30:00'),
  -- R010 高频撤单预警（非阻断）- OPEN 待处理
  (800003,'EVT20240504001',1010,'BEHAVIOR_MONITOR','MEDIUM',0,300001,400001,NULL,
   'R010: 客户今日撤单已达 11 次，超过阈值 10（预警）',
   'OPEN',NULL,NULL,'2024-05-04 14:20:00'),
  -- R001 客户状态异常（阻断）- 已处理 HANDLED
  (800004,'EVT20240505001',1001,'IDENTITY_CHECK','HIGH',1,300003,400003,NULL,
   'R001: 客户状态 FROZEN 非 NORMAL，禁止交易',
   'HANDLED',900001,'2024-05-05 10:30:00','2024-05-05 09:15:00'),
  -- R004 证券停牌（阻断）- 已关闭 CLOSED
  (800005,'EVT20240505002',1004,'SECURITY_CHECK','MEDIUM',1,300001,400001,NULL,
   'R004: 证券 600001 上市状态非正常（0），不可交易',
   'CLOSED',900001,'2024-05-05 11:00:00','2024-05-05 10:00:00'),
  -- R006 涨跌停价格违规（阻断）- OPEN 待处理
  (800006,'EVT20240506001',1006,'PRICE_CHECK','MEDIUM',1,300001,400001,NULL,
   'R006: 买入委托价 11.00 超过涨停价 10.78（浦发银行），已阻断',
   'OPEN',NULL,NULL,'2024-05-06 09:45:00'),
  -- R008 卖出持仓不足（阻断）- OPEN 待处理
  (800007,'EVT20240506002',1008,'POSITION_CHECK','HIGH',1,300002,400002,NULL,
   'R008: 卖出数量 200 超过可用持仓 100（000001），持仓不足',
   'OPEN',NULL,NULL,'2024-05-06 10:10:00'),
  -- R005 委托数量非整手（阻断）- 已忽略 IGNORED
  (800008,'EVT20240506003',1005,'ORDER_CHECK','LOW',1,300001,400001,NULL,
   'R005: 委托数量 50 不是最小交易单位 100 的整数倍',
   'IGNORED',900001,'2024-05-06 15:00:00','2024-05-06 13:30:00');

SET FOREIGN_KEY_CHECKS = 1;

SELECT '=== 测试数据导入完成 ===' AS msg;
SELECT '证券' AS 表名, COUNT(*) AS 数量 FROM md_security
UNION ALL SELECT '行情快照', COUNT(*) FROM md_market_quote
UNION ALL SELECT '客户', COUNT(*) FROM cust_customer
UNION ALL SELECT '资金账户', COUNT(*) FROM acct_fund_account
UNION ALL SELECT '股东账户', COUNT(*) FROM acct_shareholder_account
UNION ALL SELECT '持仓', COUNT(*) FROM acct_position
UNION ALL SELECT '开户申请(柜台)', COUNT(*) FROM cust_import_record
UNION ALL SELECT '开户申请(App)', COUNT(*) FROM cust_open_apply
UNION ALL SELECT '委托', COUNT(*) FROM trd_order
UNION ALL SELECT '成交', COUNT(*) FROM trd_trade
UNION ALL SELECT '费用明细', COUNT(*) FROM trd_fee_detail
UNION ALL SELECT '资产流水', COUNT(*) FROM acct_asset_journal
UNION ALL SELECT '操作日志', COUNT(*) FROM sys_operation_log
UNION ALL SELECT '风控事件', COUNT(*) FROM risk_event;
