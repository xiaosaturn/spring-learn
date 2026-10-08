package org.masterh.rabbitmqdemo;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TopicTopology {
    public static final String EXCHANGE = "lesson.topic.exchange";
    public static final String NOTIFICATION_QUEUE = "lesson.topic.notification.queue";
    public static final String NOTIFICATION_BINDING = "order.*";
    public static final String POINTS_QUEUE = "lesson.topic.points.queue";
    public static final String POINTS_BINDING = "order.paid";

    @Bean
    TopicExchange orderTopicExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue topicNotificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    Queue topicPointsQueue() {
        return QueueBuilder.durable(POINTS_QUEUE).build();
    }

    @Bean
    Binding topicNotificationBinding(@Qualifier("topicNotificationQueue") Queue queue,
                                     @Qualifier("orderTopicExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(NOTIFICATION_BINDING);
    }

    @Bean
    Binding topicPointsBinding(@Qualifier("topicPointsQueue") Queue queue,
                               @Qualifier("orderTopicExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(POINTS_BINDING);
    }
}
