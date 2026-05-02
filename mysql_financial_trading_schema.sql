

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS financial_trading
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE financial_trading;

-- =========================
-- 风控审计域（先建基础权限表）
-- =========================

DROP TABLE IF EXISTS sys_operation_log;
DROP TABLE IF EXISTS sys_operator;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS risk_event;
DROP TABLE IF EXISTS risk_rule;

CREATE TABLE sys_role (
  id BIGINT NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  role_type VARCHAR(16) NOT NULL DEFAULT 'BIZ',
  data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF',
  status CHAR(1) NOT NULL DEFAULT '1',
  description VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_role_code (role_code),
  KEY idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE sys_operator (
  id BIGINT NOT NULL,
  operator_code VARCHAR(32) NOT NULL,
  operator_name VARCHAR(64) NOT NULL,
  role_id BIGINT NOT NULL,
  login_name VARCHAR(64) NOT NULL,
  login_password_hash VARCHAR(128) NOT NULL,
  status CHAR(1) NOT NULL DEFAULT '1',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_operator_operator_code (operator_code),
  UNIQUE KEY uk_sys_operator_login_name (login_name),
  KEY idx_sys_operator_status (status),
  CONSTRAINT fk_sys_operator_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作员表';

CREATE TABLE risk_rule (
  id BIGINT NOT NULL,
  rule_code VARCHAR(32) NOT NULL,
  rule_name VARCHAR(64) NOT NULL,
  rule_stage VARCHAR(16) NOT NULL,
  rule_type VARCHAR(32) NOT NULL,
  rule_expression VARCHAR(255) NULL,
  param_json TEXT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  block_flag TINYINT(1) NOT NULL DEFAULT 1,
  status CHAR(1) NOT NULL DEFAULT '1',
  effective_date DATE NOT NULL,
  expire_date DATE NULL,
  remark VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_risk_rule_rule_code (rule_code),
  KEY idx_risk_rule_stage (rule_stage),
  KEY idx_risk_rule_type (rule_type),
  KEY idx_risk_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控规则表';

CREATE TABLE risk_event (
  id BIGINT NOT NULL,
  event_no VARCHAR(32) NOT NULL,
  rule_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  is_blocking TINYINT(1) NOT NULL DEFAULT 1,
  customer_id BIGINT NULL,
  fund_account_id BIGINT NULL,
  order_id BIGINT NULL,
  security_id BIGINT NULL,
  hit_message VARCHAR(255) NOT NULL,
  event_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  handled_by BIGINT NULL,
  handled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_risk_event_event_no (event_no),
  KEY idx_risk_event_type (event_type),
  KEY idx_risk_event_level (risk_level),
  KEY idx_risk_event_customer (customer_id),
  KEY idx_risk_event_fund_account (fund_account_id),
  KEY idx_risk_event_order (order_id),
  KEY idx_risk_event_status (event_status),
  KEY idx_risk_event_created_at (created_at),
  CONSTRAINT fk_risk_event_rule FOREIGN KEY (rule_id) REFERENCES risk_rule (id),
  CONSTRAINT fk_risk_event_handler FOREIGN KEY (handled_by) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险事件表';

-- =========================
-- 基础主数据域
-- =========================

DROP TABLE IF EXISTS md_customer_template_holding;
DROP TABLE IF EXISTS md_customer_template;
DROP TABLE IF EXISTS md_tax_rule;
DROP TABLE IF EXISTS md_commission_rule;
DROP TABLE IF EXISTS md_market_quote;
DROP TABLE IF EXISTS md_security;
DROP TABLE IF EXISTS md_dict_item;
DROP TABLE IF EXISTS md_dict_type;

CREATE TABLE md_dict_type (
  id BIGINT NOT NULL,
  dict_code VARCHAR(32) NOT NULL,
  dict_name VARCHAR(64) NOT NULL,
  status CHAR(1) NOT NULL DEFAULT '1',
  sort_no INT NOT NULL DEFAULT 0,
  description VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_dict_type_dict_code (dict_code),
  KEY idx_md_dict_type_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE md_dict_item (
  id BIGINT NOT NULL,
  dict_id BIGINT NOT NULL,
  item_code VARCHAR(32) NOT NULL,
  item_name VARCHAR(64) NOT NULL,
  item_value VARCHAR(128) NULL,
  sort_no INT NOT NULL DEFAULT 0,
  status CHAR(1) NOT NULL DEFAULT '1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_dict_item_dict_code (dict_id, item_code),
  KEY idx_md_dict_item_status (status),
  CONSTRAINT fk_md_dict_item_dict_id FOREIGN KEY (dict_id) REFERENCES md_dict_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';

CREATE TABLE md_security (
  id BIGINT NOT NULL,
  market_code CHAR(1) NOT NULL,
  security_code VARCHAR(16) NOT NULL,
  security_name VARCHAR(64) NOT NULL,
  security_type VARCHAR(16) NOT NULL,
  currency_code CHAR(1) NOT NULL DEFAULT '0',
  lot_size INT NOT NULL DEFAULT 100,
  price_tick DECIMAL(10,4) NOT NULL DEFAULT 0.0100,
  listed_status CHAR(1) NOT NULL DEFAULT '1',
  source_stamp_tax_rate DECIMAL(10,6) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_security_market_code_security_code (market_code, security_code),
  KEY idx_md_security_market_code (market_code),
  KEY idx_md_security_security_type (security_type),
  KEY idx_md_security_listed_status (listed_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='证券主数据表';

CREATE TABLE md_market_quote (
  id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  trade_date DATE NOT NULL,
  quote_time DATETIME NOT NULL,
  current_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  prev_close_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  upper_limit_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  lower_limit_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  volume BIGINT NULL,
  amount DECIMAL(18,4) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_market_quote_security_id_quote_time (security_id, quote_time),
  KEY idx_md_market_quote_trade_date (trade_date),
  CONSTRAINT fk_md_market_quote_security_id FOREIGN KEY (security_id) REFERENCES md_security (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行情快照表';

CREATE TABLE md_commission_rule (
  id BIGINT NOT NULL,
  acct_cls_code VARCHAR(16) NOT NULL,
  market_code CHAR(1) NOT NULL,
  security_type VARCHAR(16) NOT NULL,
  commission_rate DECIMAL(10,6) NOT NULL DEFAULT 0.000000,
  min_fee DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  max_fee DECIMAL(18,4) NULL,
  effective_date DATE NOT NULL,
  expire_date DATE NULL,
  status CHAR(1) NOT NULL DEFAULT '1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_commission_rule (acct_cls_code, market_code, security_type, effective_date),
  KEY idx_md_commission_rule_acct_cls_code (acct_cls_code),
  KEY idx_md_commission_rule_market_code (market_code),
  KEY idx_md_commission_rule_security_type (security_type),
  KEY idx_md_commission_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金规则表';

CREATE TABLE md_tax_rule (
  id BIGINT NOT NULL,
  fee_type VARCHAR(32) NOT NULL,
  market_code CHAR(1) NOT NULL,
  security_type VARCHAR(16) NOT NULL,
  trade_direction CHAR(1) NOT NULL,
  tax_rate DECIMAL(10,6) NOT NULL DEFAULT 0.000000,
  calc_mode VARCHAR(16) NOT NULL DEFAULT 'RATE',
  min_fee DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  effective_date DATE NOT NULL,
  expire_date DATE NULL,
  status CHAR(1) NOT NULL DEFAULT '1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_tax_rule (fee_type, market_code, security_type, trade_direction, effective_date),
  KEY idx_md_tax_rule_fee_type (fee_type),
  KEY idx_md_tax_rule_market_code (market_code),
  KEY idx_md_tax_rule_security_type (security_type),
  KEY idx_md_tax_rule_trade_direction (trade_direction),
  KEY idx_md_tax_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='税费规则表';

CREATE TABLE md_customer_template (
  id BIGINT NOT NULL,
  template_code VARCHAR(32) NOT NULL,
  acct_cls_code VARCHAR(16) NOT NULL,
  template_name VARCHAR(64) NOT NULL,
  currency_code CHAR(1) NOT NULL DEFAULT '0',
  init_cash_balance DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  status CHAR(1) NOT NULL DEFAULT '1',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_customer_template_template_code (template_code),
  UNIQUE KEY uk_md_customer_template_acct_cls_code (acct_cls_code),
  KEY idx_md_customer_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户模板头表';

CREATE TABLE md_customer_template_holding (
  id BIGINT NOT NULL,
  template_id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  init_qty BIGINT NOT NULL DEFAULT 0,
  cost_price DECIMAL(18,4) NULL,
  sort_no INT NOT NULL DEFAULT 0,
  status CHAR(1) NOT NULL DEFAULT '1',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_md_customer_template_holding (template_id, security_id),
  KEY idx_md_customer_template_holding_status (status),
  CONSTRAINT fk_md_customer_template_holding_template_id FOREIGN KEY (template_id) REFERENCES md_customer_template (id),
  CONSTRAINT fk_md_customer_template_holding_security_id FOREIGN KEY (security_id) REFERENCES md_security (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户模板持仓明细表';

-- =========================
-- 开户审核域
-- =========================

DROP TABLE IF EXISTS cust_open_audit_log;
DROP TABLE IF EXISTS cust_import_record;
DROP TABLE IF EXISTS cust_import_batch;
DROP TABLE IF EXISTS cust_customer;

CREATE TABLE cust_import_batch (
  id BIGINT NOT NULL,
  batch_no VARCHAR(32) NOT NULL,
  import_mode VARCHAR(16) NOT NULL DEFAULT 'BATCH',
  source_file_name VARCHAR(128) NULL,
  total_count INT NOT NULL DEFAULT 0,
  pass_count INT NOT NULL DEFAULT 0,
  reject_count INT NOT NULL DEFAULT 0,
  batch_status VARCHAR(16) NOT NULL DEFAULT 'INIT',
  imported_by BIGINT NULL,
  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cust_import_batch_batch_no (batch_no),
  KEY idx_cust_import_batch_import_mode (import_mode),
  KEY idx_cust_import_batch_batch_status (batch_status),
  CONSTRAINT fk_cust_import_batch_imported_by FOREIGN KEY (imported_by) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户导入批次表';

CREATE TABLE cust_customer (
  id BIGINT NOT NULL,
  customer_code VARCHAR(32) NOT NULL,
  customer_name VARCHAR(64) NOT NULL,
  id_type VARCHAR(16) NOT NULL,
  id_no VARCHAR(64) NOT NULL,
  customer_type VARCHAR(16) NOT NULL DEFAULT 'INDIVIDUAL',
  acct_cls_code VARCHAR(16) NOT NULL,
  customer_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  risk_level VARCHAR(16) NOT NULL DEFAULT 'R2',
  open_date DATE NOT NULL,
  source_record_id BIGINT NULL,
  mobile VARCHAR(32) NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cust_customer_customer_code (customer_code),
  UNIQUE KEY uk_cust_customer_id_type_id_no (id_type, id_no),
  KEY idx_cust_customer_customer_type (customer_type),
  KEY idx_cust_customer_acct_cls_code (acct_cls_code),
  KEY idx_cust_customer_customer_status (customer_status),
  KEY idx_cust_customer_open_date (open_date),
  KEY idx_cust_customer_source_record_id (source_record_id),
  CONSTRAINT fk_cust_customer_created_by FOREIGN KEY (created_by) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户主表';

CREATE TABLE cust_import_record (
  id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  record_no VARCHAR(32) NOT NULL,
  row_no INT NOT NULL DEFAULT 0,
  customer_name VARCHAR(64) NOT NULL,
  id_type VARCHAR(16) NOT NULL,
  id_no VARCHAR(64) NOT NULL,
  acct_cls_code VARCHAR(16) NOT NULL,
  sh_shareholder_acct VARCHAR(32) NULL,
  sz_shareholder_acct VARCHAR(32) NULL,
  import_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  validate_message VARCHAR(255) NULL,
  review_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_AUDIT',
  created_customer_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cust_import_record_record_no (record_no),
  UNIQUE KEY uk_cust_import_record_batch_row (batch_id, row_no),
  KEY idx_cust_import_record_id_type_id_no (id_type, id_no),
  KEY idx_cust_import_record_import_status (import_status),
  KEY idx_cust_import_record_review_status (review_status),
  CONSTRAINT fk_cust_import_record_batch_id FOREIGN KEY (batch_id) REFERENCES cust_import_batch (id),
  CONSTRAINT fk_cust_import_record_created_customer_id FOREIGN KEY (created_customer_id) REFERENCES cust_customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户导入明细表';

ALTER TABLE cust_customer
  ADD CONSTRAINT fk_cust_customer_source_record_id FOREIGN KEY (source_record_id) REFERENCES cust_import_record (id);

CREATE TABLE cust_open_audit_log (
  id BIGINT NOT NULL,
  record_id BIGINT NOT NULL,
  audit_no VARCHAR(32) NOT NULL,
  audit_result VARCHAR(16) NOT NULL,
  audit_comment VARCHAR(255) NULL,
  auditor_id BIGINT NOT NULL,
  previous_status VARCHAR(16) NOT NULL,
  current_status VARCHAR(16) NOT NULL,
  audited_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cust_open_audit_log_audit_no (audit_no),
  KEY idx_cust_open_audit_log_audit_result (audit_result),
  CONSTRAINT fk_cust_open_audit_log_record_id FOREIGN KEY (record_id) REFERENCES cust_import_record (id),
  CONSTRAINT fk_cust_open_audit_log_auditor_id FOREIGN KEY (auditor_id) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开户审核日志表';

-- =========================
-- 账户资产域
-- =========================

DROP TABLE IF EXISTS acct_asset_journal;
DROP TABLE IF EXISTS acct_position;
DROP TABLE IF EXISTS acct_shareholder_account;
DROP TABLE IF EXISTS acct_fund_account;

CREATE TABLE acct_fund_account (
  id BIGINT NOT NULL,
  fund_account_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  currency_code CHAR(1) NOT NULL DEFAULT '0',
  current_balance DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  available_balance DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  frozen_balance DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  version INT NOT NULL DEFAULT 0,
  opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_acct_fund_account_no (fund_account_no),
  KEY idx_acct_fund_account_status (status),
  CONSTRAINT fk_acct_fund_account_customer_id FOREIGN KEY (customer_id) REFERENCES cust_customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金账户表';

CREATE TABLE acct_shareholder_account (
  id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  market_code CHAR(1) NOT NULL,
  shareholder_account_no VARCHAR(32) NOT NULL,
  account_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  remark VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_acct_shareholder_account_customer_market (customer_id, market_code),
  UNIQUE KEY uk_acct_shareholder_account_no (shareholder_account_no),
  KEY idx_acct_shareholder_account_status (account_status),
  CONSTRAINT fk_acct_shareholder_account_customer_id FOREIGN KEY (customer_id) REFERENCES cust_customer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股东账户表';

CREATE TABLE acct_position (
  id BIGINT NOT NULL,
  fund_account_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  market_code CHAR(1) NOT NULL,
  security_code_snapshot VARCHAR(16) NOT NULL,
  total_qty BIGINT NOT NULL DEFAULT 0,
  available_qty BIGINT NOT NULL DEFAULT 0,
  frozen_qty BIGINT NOT NULL DEFAULT 0,
  cost_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  last_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  market_value DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  position_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  version INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_acct_position_fund_security (fund_account_id, security_id),
  KEY idx_acct_position_market_code (market_code),
  KEY idx_acct_position_status (position_status),
  CONSTRAINT fk_acct_position_fund_account_id FOREIGN KEY (fund_account_id) REFERENCES acct_fund_account (id),
  CONSTRAINT fk_acct_position_customer_id FOREIGN KEY (customer_id) REFERENCES cust_customer (id),
  CONSTRAINT fk_acct_position_security_id FOREIGN KEY (security_id) REFERENCES md_security (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前持仓表';

CREATE TABLE acct_asset_journal (
  id BIGINT NOT NULL,
  journal_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  fund_account_id BIGINT NOT NULL,
  security_id BIGINT NULL,
  asset_type VARCHAR(16) NOT NULL,
  change_type VARCHAR(32) NOT NULL,
  direction CHAR(1) NOT NULL,
  before_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  change_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  after_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  ref_type VARCHAR(16) NOT NULL,
  ref_id BIGINT NULL,
  ref_no VARCHAR(32) NULL,
  operator_id BIGINT NULL,
  occur_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_acct_asset_journal_no (journal_no),
  KEY idx_acct_asset_journal_customer_id (customer_id),
  KEY idx_acct_asset_journal_fund_account_id (fund_account_id),
  KEY idx_acct_asset_journal_security_id (security_id),
  KEY idx_acct_asset_journal_asset_type (asset_type),
  KEY idx_acct_asset_journal_change_type (change_type),
  KEY idx_acct_asset_journal_ref_type_ref_no (ref_type, ref_no),
  KEY idx_acct_asset_journal_occur_time (occur_time),
  CONSTRAINT fk_acct_asset_journal_operator_id FOREIGN KEY (operator_id) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产流水表';

-- =========================
-- 交易报盘域
-- =========================

DROP TABLE IF EXISTS trd_fee_detail;
DROP TABLE IF EXISTS trd_trade;
DROP TABLE IF EXISTS rpt_order_reply;
DROP TABLE IF EXISTS trd_cancel_request;
DROP TABLE IF EXISTS rpt_order_dispatch;
DROP TABLE IF EXISTS trd_order;

CREATE TABLE trd_order (
  id BIGINT NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  fund_account_id BIGINT NOT NULL,
  shareholder_account_id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  market_code CHAR(1) NOT NULL,
  security_code VARCHAR(16) NOT NULL,
  security_name_snapshot VARCHAR(64) NOT NULL,
  trade_direction CHAR(1) NOT NULL,
  order_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  order_qty BIGINT NOT NULL DEFAULT 0,
  order_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  filled_qty BIGINT NOT NULL DEFAULT 0,
  filled_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  avg_fill_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  frozen_cash DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  frozen_qty BIGINT NOT NULL DEFAULT 0,
  order_status VARCHAR(16) NOT NULL DEFAULT 'INIT',
  risk_check_result VARCHAR(16) NOT NULL DEFAULT 'PASS',
  dispatch_status VARCHAR(16) NOT NULL DEFAULT 'WAIT',
  source_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trd_order_order_no (order_no),
  KEY idx_trd_order_customer_created (customer_id, created_at),
  KEY idx_trd_order_fund_status (fund_account_id, order_status),
  KEY idx_trd_order_security_status (security_id, order_status),
  KEY idx_trd_order_direction (trade_direction),
  KEY idx_trd_order_status (order_status),
  CONSTRAINT fk_trd_order_customer_id FOREIGN KEY (customer_id) REFERENCES cust_customer (id),
  CONSTRAINT fk_trd_order_fund_account_id FOREIGN KEY (fund_account_id) REFERENCES acct_fund_account (id),
  CONSTRAINT fk_trd_order_shareholder_account_id FOREIGN KEY (shareholder_account_id) REFERENCES acct_shareholder_account (id),
  CONSTRAINT fk_trd_order_security_id FOREIGN KEY (security_id) REFERENCES md_security (id),
  CONSTRAINT fk_trd_order_created_by FOREIGN KEY (created_by) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委托主表';

CREATE TABLE rpt_order_dispatch (
  id BIGINT NOT NULL,
  dispatch_no VARCHAR(32) NOT NULL,
  order_id BIGINT NOT NULL,
  dispatch_type VARCHAR(16) NOT NULL DEFAULT 'ORDER',
  request_seq_no VARCHAR(128) NOT NULL,
  request_payload TEXT NULL,
  dispatch_status VARCHAR(16) NOT NULL DEFAULT 'INIT',
  external_order_no VARCHAR(32) NULL,
  sent_at DATETIME NULL,
  reply_deadline DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rpt_order_dispatch_dispatch_no (dispatch_no),
  UNIQUE KEY uk_rpt_order_dispatch_request_seq_no (request_seq_no),
  KEY idx_rpt_order_dispatch_order_id (order_id),
  KEY idx_rpt_order_dispatch_type (dispatch_type),
  KEY idx_rpt_order_dispatch_status (dispatch_status),
  CONSTRAINT fk_rpt_order_dispatch_order_id FOREIGN KEY (order_id) REFERENCES trd_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报盘流水表';

CREATE TABLE trd_cancel_request (
  id BIGINT NOT NULL,
  cancel_no VARCHAR(32) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no_snapshot VARCHAR(32) NOT NULL,
  request_qty BIGINT NULL,
  cancel_reason VARCHAR(128) NULL,
  cancel_status VARCHAR(16) NOT NULL DEFAULT 'INIT',
  dispatch_id BIGINT NULL,
  requested_by BIGINT NULL,
  request_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_time DATETIME NULL,
  reject_message VARCHAR(255) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trd_cancel_request_cancel_no (cancel_no),
  KEY idx_trd_cancel_request_order_id (order_id),
  KEY idx_trd_cancel_request_status (cancel_status),
  CONSTRAINT fk_trd_cancel_request_order_id FOREIGN KEY (order_id) REFERENCES trd_order (id),
  CONSTRAINT fk_trd_cancel_request_dispatch_id FOREIGN KEY (dispatch_id) REFERENCES rpt_order_dispatch (id),
  CONSTRAINT fk_trd_cancel_request_requested_by FOREIGN KEY (requested_by) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='撤单申请表';

CREATE TABLE rpt_order_reply (
  id BIGINT NOT NULL,
  reply_no VARCHAR(32) NOT NULL,
  dispatch_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  cancel_request_id BIGINT NULL,
  reply_type VARCHAR(16) NOT NULL,
  external_order_no VARCHAR(32) NULL,
  external_trade_no VARCHAR(32) NULL,
  accept_code VARCHAR(32) NULL,
  accept_message VARCHAR(255) NULL,
  reply_payload TEXT NULL,
  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rpt_order_reply_reply_no (reply_no),
  KEY idx_rpt_order_reply_dispatch_id (dispatch_id),
  KEY idx_rpt_order_reply_order_id (order_id),
  KEY idx_rpt_order_reply_cancel_request_id (cancel_request_id),
  KEY idx_rpt_order_reply_reply_type (reply_type),
  CONSTRAINT fk_rpt_order_reply_dispatch_id FOREIGN KEY (dispatch_id) REFERENCES rpt_order_dispatch (id),
  CONSTRAINT fk_rpt_order_reply_order_id FOREIGN KEY (order_id) REFERENCES trd_order (id),
  CONSTRAINT fk_rpt_order_reply_cancel_request_id FOREIGN KEY (cancel_request_id) REFERENCES trd_cancel_request (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报盘回报表';

CREATE TABLE trd_trade (
  id BIGINT NOT NULL,
  trade_no VARCHAR(32) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  customer_id BIGINT NOT NULL,
  fund_account_id BIGINT NOT NULL,
  security_id BIGINT NOT NULL,
  market_code CHAR(1) NOT NULL,
  security_code VARCHAR(16) NOT NULL,
  trade_direction CHAR(1) NOT NULL,
  trade_price DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  trade_qty BIGINT NOT NULL DEFAULT 0,
  trade_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  commission_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  tax_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  other_fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  net_settle_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  trade_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  settle_status VARCHAR(16) NOT NULL DEFAULT 'SETTLED',
  source_reply_id BIGINT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trd_trade_trade_no (trade_no),
  KEY idx_trd_trade_order_id (order_id),
  KEY idx_trd_trade_customer_id (customer_id),
  KEY idx_trd_trade_fund_trade_time (fund_account_id, trade_time),
  KEY idx_trd_trade_direction (trade_direction),
  KEY idx_trd_trade_trade_time (trade_time),
  KEY idx_trd_trade_settle_status (settle_status),
  CONSTRAINT fk_trd_trade_order_id FOREIGN KEY (order_id) REFERENCES trd_order (id),
  CONSTRAINT fk_trd_trade_source_reply_id FOREIGN KEY (source_reply_id) REFERENCES rpt_order_reply (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成交表';

CREATE TABLE trd_fee_detail (
  id BIGINT NOT NULL,
  biz_type VARCHAR(16) NOT NULL,
  biz_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  trade_id BIGINT NULL,
  fee_type VARCHAR(32) NOT NULL,
  calc_base DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  fee_rate DECIMAL(10,6) NOT NULL DEFAULT 0.000000,
  fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  currency_code CHAR(1) NOT NULL DEFAULT '0',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  remark VARCHAR(255) NULL,
  PRIMARY KEY (id),
  KEY idx_trd_fee_detail_biz (biz_type, biz_id),
  KEY idx_trd_fee_detail_fee_type (fee_type),
  CONSTRAINT fk_trd_fee_detail_order_id FOREIGN KEY (order_id) REFERENCES trd_order (id),
  CONSTRAINT fk_trd_fee_detail_trade_id FOREIGN KEY (trade_id) REFERENCES trd_trade (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费用明细表';

CREATE TABLE sys_operation_log (
  id BIGINT NOT NULL,
  log_no VARCHAR(32) NOT NULL,
  operator_id BIGINT NOT NULL,
  module_name VARCHAR(32) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_id BIGINT NULL,
  biz_no VARCHAR(32) NULL,
  operation_type VARCHAR(32) NOT NULL,
  operation_result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  request_ip VARCHAR(64) NULL,
  operation_content VARCHAR(255) NULL,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_operation_log_log_no (log_no),
  KEY idx_sys_operation_log_operator_occurred (operator_id, occurred_at),
  KEY idx_sys_operation_log_module_name (module_name),
  KEY idx_sys_operation_log_biz_type_no (biz_type, biz_no),
  KEY idx_sys_operation_log_occurred_at (occurred_at),
  CONSTRAINT fk_sys_operation_log_operator_id FOREIGN KEY (operator_id) REFERENCES sys_operator (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

SET FOREIGN_KEY_CHECKS = 1;

-- =========================
-- Android 第一阶段：App 用户与开户申请
-- =========================
DROP TABLE IF EXISTS app_login_session;
DROP TABLE IF EXISTS cust_open_apply;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
  id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  mobile VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_username (username),
  UNIQUE KEY uk_app_user_mobile (mobile),
  KEY idx_app_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App用户表';

CREATE TABLE cust_open_apply (
  id BIGINT NOT NULL,
  apply_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  customer_name VARCHAR(64) NOT NULL,
  id_type VARCHAR(16) NOT NULL,
  id_no VARCHAR(64) NOT NULL,
  mobile VARCHAR(32) NOT NULL,
  acct_cls_code VARCHAR(16) NOT NULL,
  shareholder_market VARCHAR(8) NOT NULL DEFAULT 'SH',
  sh_account VARCHAR(32) NOT NULL,
  sz_account VARCHAR(32) NOT NULL,
  apply_status VARCHAR(16) NOT NULL DEFAULT 'WAIT_AUDIT',
  audit_comment VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cust_open_apply_apply_no (apply_no),
  UNIQUE KEY uk_cust_open_apply_sh_account (sh_account),
  UNIQUE KEY uk_cust_open_apply_sz_account (sz_account),
  KEY idx_open_apply_user_id (user_id),
  KEY idx_open_apply_status (apply_status),
  KEY idx_open_apply_id_type_no (id_type, id_no),
  KEY idx_open_apply_id_status (id_type, id_no, apply_status),
  CONSTRAINT fk_cust_open_apply_user_id FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开户申请表(App端)';

CREATE TABLE app_login_session (
  id BIGINT NOT NULL,
  token VARCHAR(80) NOT NULL,
  user_id BIGINT NOT NULL,
  expire_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_login_session_token (token),
  KEY idx_app_login_session_user_id (user_id),
  KEY idx_app_login_session_expire_at (expire_at),
  CONSTRAINT fk_app_login_session_user_id FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='App登录会话表';

-- =========================
-- 初始化数据：客户账户模板
-- =========================
INSERT INTO md_customer_template (
  id, template_code, acct_cls_code, template_name, currency_code, init_cash_balance, status, remark, created_at, updated_at
) VALUES
  (910001, 'TPL_NORMAL', 'NORMAL', '普通账户模板', '0', 100000.0000, '1', '默认普通账户', NOW(), NOW()),
  (910002, 'TPL_VIP', 'VIP', 'VIP账户模板', '0', 500000.0000, '1', '默认VIP账户', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  template_code = VALUES(template_code),
  template_name = VALUES(template_name),
  currency_code = VALUES(currency_code),
  init_cash_balance = VALUES(init_cash_balance),
  status = VALUES(status),
  remark = VALUES(remark),
  updated_at = NOW();

-- =========================
-- 初始化数据：默认操作员账号
-- 说明：当前后端为明文密码校验，请与登录页输入保持一致
-- =========================
INSERT INTO sys_role (
  id, role_code, role_name, role_type, data_scope, status, description, created_at, updated_at
) VALUES (
  900001, 'OPERATOR', '操作员', 'BIZ', 'ALL', '1', '默认操作员角色', NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  role_type = VALUES(role_type),
  data_scope = VALUES(data_scope),
  status = VALUES(status),
  description = VALUES(description),
  updated_at = NOW();

INSERT INTO sys_operator (
  id, operator_code, operator_name, role_id, login_name, login_password_hash, status, created_at, updated_at
) VALUES (
  900001, 'OP900001', '系统操作员', 900001, 'admin', '123456', '1', NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
  operator_name = VALUES(operator_name),
  role_id = VALUES(role_id),
  login_password_hash = VALUES(login_password_hash),
  status = VALUES(status),
  updated_at = NOW();
