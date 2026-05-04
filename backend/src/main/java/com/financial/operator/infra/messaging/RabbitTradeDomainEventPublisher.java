package com.financial.operator.infra.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "financial.messaging.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(RabbitTemplate.class)
public class RabbitTradeDomainEventPublisher implements TradeDomainEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitTradeDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishTradeSettled(TradeSettledPayload payload) {
        rabbitTemplate.convertAndSend(RabbitMqInfraConfig.EXCHANGE, RabbitMqInfraConfig.ROUTING_TRADE_SETTLED, payload);
    }
}
