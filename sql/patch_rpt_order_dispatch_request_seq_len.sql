-- 撤单/下单幂等键 request_seq_no 原 VARCHAR(32)，App 端常见格式如
-- APP_CANCEL_<timestamp>_<uuid片段> 会超过 32 字符，导致 INSERT 失败 → HTTP 500。
-- 执行本脚本后重启应用。

ALTER TABLE rpt_order_dispatch
  MODIFY COLUMN request_seq_no VARCHAR(128) NOT NULL COMMENT '客户端幂等流水号';
