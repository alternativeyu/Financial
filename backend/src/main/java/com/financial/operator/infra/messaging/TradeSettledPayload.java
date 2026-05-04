package com.financial.operator.infra.messaging;

import java.math.BigDecimal;

/**
 * 成交清算完成后投递到 MQ 的消息体（审计异步落库 + 通知占位）。
 */
public record TradeSettledPayload(
        long tradeId,
        String tradeNo,
        String orderNo,
        long orderId,
        long customerId,
        Long operatorId,
        String sourceType,
        String tradeDirection,
        long fillQty,
        BigDecimal fillPrice,
        BigDecimal tradeAmount,
        BigDecimal commissionAmount,
        BigDecimal taxAmount,
        String orderStatusAfter
) {
}
