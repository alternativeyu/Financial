package com.financial.operator.infra.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "financial.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqInfraConfig {

    public static final String EXCHANGE = "financial.topic";
    public static final String ROUTING_TRADE_SETTLED = "trade.settled";
    public static final String QUEUE_TRADE_AUDIT = "financial.trade.audit";
    public static final String QUEUE_TRADE_NOTIFY = "financial.trade.notify";

    @Bean
    public TopicExchange financialTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue tradeAuditQueue() {
        return new Queue(QUEUE_TRADE_AUDIT, true);
    }

    @Bean
    public Queue tradeNotifyQueue() {
        return new Queue(QUEUE_TRADE_NOTIFY, true);
    }

    @Bean
    public Binding tradeAuditBinding(TopicExchange financialTopicExchange, Queue tradeAuditQueue) {
        return BindingBuilder.bind(tradeAuditQueue).to(financialTopicExchange).with(ROUTING_TRADE_SETTLED);
    }

    @Bean
    public Binding tradeNotifyBinding(TopicExchange financialTopicExchange, Queue tradeNotifyQueue) {
        return BindingBuilder.bind(tradeNotifyQueue).to(financialTopicExchange).with(ROUTING_TRADE_SETTLED);
    }

    @Bean
    public Jackson2JsonMessageConverter financialJacksonAmqpConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplateCustomizer financialJacksonRabbitTemplateCustomizer(Jackson2JsonMessageConverter financialJacksonAmqpConverter) {
        return (RabbitTemplate template) -> template.setMessageConverter(financialJacksonAmqpConverter);
    }
}
