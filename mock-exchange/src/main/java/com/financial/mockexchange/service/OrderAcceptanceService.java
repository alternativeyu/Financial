package com.financial.mockexchange.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 模拟交易所：报盘受理（与原先 operator-backend 进程内逻辑一致，独立进程执行）。
 */
@Service
public class OrderAcceptanceService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;

    public OrderAcceptanceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void acceptOrder(long dispatchId, long orderId, String orderNo) {
        Integer existed = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM rpt_order_reply
                WHERE dispatch_id = ? AND reply_type = 'ORDER_ACCEPT'
                """,
                Integer.class,
                dispatchId
        );
        if (existed != null && existed > 0) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, order_no FROM trd_order WHERE id = ? LIMIT 1",
                orderId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("委托不存在: " + orderId);
        }
        String dbNo = rows.get(0).get("order_no") == null ? null : String.valueOf(rows.get(0).get("order_no"));
        if (dbNo == null || !dbNo.equals(orderNo)) {
            throw new IllegalArgumentException("委托单号与 id 不匹配");
        }

        long replyId = nextId();
        String replyNo = "RP" + LocalDate.now().format(DATE_FMT) + String.format("%06d", replyId % 1_000_000);
        jdbcTemplate.update(
                """
                INSERT INTO rpt_order_reply
                (id, reply_no, dispatch_id, order_id, cancel_request_id, reply_type, external_order_no, external_trade_no,
                 accept_code, accept_message, reply_payload, received_at)
                VALUES (?, ?, ?, ?, NULL, 'ORDER_ACCEPT', ?, NULL, '0', '受理成功', ?, NOW())
                """,
                replyId, replyNo, dispatchId, orderId, "EX" + orderNo, "{\"result\":\"ACCEPTED\",\"source\":\"MOCK_EXCHANGE\"}"
        );

        jdbcTemplate.update("UPDATE rpt_order_dispatch SET dispatch_status = 'ACCEPTED' WHERE id = ?", dispatchId);
        jdbcTemplate.update("UPDATE trd_order SET order_status = 'REPORTED', dispatch_status = 'ACCEPTED', updated_at = NOW() WHERE id = ?", orderId);
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
