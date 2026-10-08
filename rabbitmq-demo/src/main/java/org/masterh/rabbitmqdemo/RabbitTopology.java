package org.masterh.rabbitmqdemo;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitTopology {
    public static final String TASK_EXCHANGE = "lesson.task.exchange";
    public static final String TASK_QUEUE = "lesson.task.queue";
    public static final String TASK_KEY = "task.created";
    public static final String DEAD_EXCHANGE = "lesson.task.dlx";
    public static final String DEAD_QUEUE = "lesson.task.dlq";
    public static final String DEAD_KEY = "task.failed";

    @Bean
    DirectExchange taskExchange() {
        return new DirectExchange(TASK_EXCHANGE, true, false);
    }

    @Bean
    Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_KEY)
                .build();
    }

    @Bean
    Binding taskBinding(@Qualifier("taskQueue") Queue queue,
                        @Qualifier("taskExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(TASK_KEY);
    }

    @Bean
    DirectExchange deadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue deadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding deadBinding(@Qualifier("deadQueue") Queue queue,
                        @Qualifier("deadExchange") DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(DEAD_KEY);
    }

    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("org.masterh.rabbitmqdemo");
    }
}
