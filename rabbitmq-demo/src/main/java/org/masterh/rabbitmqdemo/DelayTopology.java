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
public class DelayTopology {
    public static final int DELAY_MS = 5000;
    public static final String WAIT_EXCHANGE = "lesson.delay.exchange";
    public static final String WAIT_QUEUE = "lesson.delay.wait.queue";
    public static final String WAIT_KEY = "delay.wait";
    public static final String READY_EXCHANGE = "lesson.delay.ready.exchange";
    public static final String READY_QUEUE = "lesson.delay.ready.queue";
    public static final String READY_KEY = "delay.ready";

    @Bean
    DirectExchange delayWaitExchange() {
        return new DirectExchange(WAIT_EXCHANGE, true, false);
    }

    @Bean
    Queue delayWaitQueue() {
        // No consumer subscribes to this queue; expiration forwards messages to the ready exchange.
        return QueueBuilder.durable(WAIT_QUEUE)
                .ttl(DELAY_MS)
                .deadLetterExchange(READY_EXCHANGE)
                .deadLetterRoutingKey(READY_KEY)
                .build();
    }

    @Bean
    Binding delayWaitBinding(@Qualifier("delayWaitQueue") Queue queue,
                             @Qualifier("delayWaitExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(WAIT_KEY);
    }

    @Bean
    DirectExchange delayReadyExchange() {
        return new DirectExchange(READY_EXCHANGE, true, false);
    }

    @Bean
    Queue delayReadyQueue() {
        return QueueBuilder.durable(READY_QUEUE).build();
    }

    @Bean
    Binding delayReadyBinding(@Qualifier("delayReadyQueue") Queue queue,
                              @Qualifier("delayReadyExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(READY_KEY);
    }
}
