package com.financial.operator.infra.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 清算后异步：审计写入 sys_operation_log；通知队列占位（后续可接 app_notice / 推送）。
 */
@Component
@ConditionalOnProperty(name = "financial.messaging.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(RabbitTemplate.class)
public class TradeSettlementRabbitListeners {

    private static final Logger log = LoggerFactory.getLogger(TradeSettlementRabbitListeners.class);
    private static final long SYSTEM_OPERATOR_ID = 900001L;

    private final JdbcTemplate jdbcTemplate;

    public TradeSettlementRabbitListeners(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @RabbitListener(queues = RabbitMqInfraConfig.QUEUE_TRADE_AUDIT)
    public void onTradeSettledAudit(TradeSettledPayload p) {
        long opId = p.operatorId() != null ? p.operatorId() : SYSTEM_OPERATOR_ID;
        String logNo = ("SL" + UUID.randomUUID().toString().replace("-", "")).substring(0, 32);
        String content = "成交清算 orderNo=" + p.orderNo() + " tradeNo=" + p.tradeNo()
                + " qty=" + p.fillQty() + " status=" + p.orderStatusAfter();
        if (content.length() > 250) {
            content = content.substring(0, 250);
        }
        long id = nextId();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO sys_operation_log
                    (id, log_no, operator_id, module_name, biz_type, biz_id, biz_no, operation_type, operation_result, request_ip, operation_content, occurred_at)
                    VALUES (?, ?, ?, 'TRADE', 'SETTLEMENT', ?, ?, 'FILL_SETTLE', 'SUCCESS', NULL, ?, NOW())
                    """,
                    id, logNo, opId, p.tradeId(), p.tradeNo(), content
            );
        } catch (Exception e) {
            log.warn("异步审计落库失败 tradeNo={}: {}", p.tradeNo(), e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMqInfraConfig.QUEUE_TRADE_NOTIFY)
    public void onTradeSettledNotify(TradeSettledPayload p) {
        log.info("异步通知占位: customerId={} orderNo={} tradeNo={} sourceType={}",
                p.customerId(), p.orderNo(), p.tradeNo(), p.sourceType());
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
