package com.financial.operator.infra.messaging;

public interface TradeDomainEventPublisher {

    void publishTradeSettled(TradeSettledPayload payload);
}
