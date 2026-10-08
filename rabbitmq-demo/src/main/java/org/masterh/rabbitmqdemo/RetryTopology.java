package org.masterh.rabbitmqdemo;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RetryTopology {
    public static final String TASK_EXCHANGE = "lesson.retry.exchange";
    public static final String TASK_QUEUE = "lesson.retry.queue";
    public static final String TASK_KEY = "retry.created";
    public static final String DEAD_EXCHANGE = "lesson.retry.dlx";
    public static final String DEAD_QUEUE = "lesson.retry.dlq";
    public static final String DEAD_KEY = "retry.failed";

    @Bean
    DirectExchange retryTaskExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    @Bean
    Queue retryTaskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_KEY)
                .build();
    }

    @Bean
    Binding retryTaskBinding(@Qualifier("retryTaskQueue") Queue queue,
                             @Qualifier("retryTaskExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(TASK_KEY);
    }

    @Bean
    DirectExchange retryDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue retryDeadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding retryDeadBinding(@Qualifier("retryDeadQueue") Queue queue,
                             @Qualifier("retryDeadExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DEAD_KEY);
    }
}
