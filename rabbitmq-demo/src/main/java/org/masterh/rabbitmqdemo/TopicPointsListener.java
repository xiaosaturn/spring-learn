package org.masterh.rabbitmqdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class TopicPointsListener {
    private static final Logger log = LoggerFactory.getLogger(TopicPointsListener.class);
    private final TopicEventLog events;

    public TopicPointsListener(TopicEventLog events) {
        this.events = events;
    }

    @RabbitListener(queues = TopicTopology.POINTS_QUEUE, ackMode = "AUTO",
            autoStartup = "${lesson.topic.points-enabled:true}")
    public void handle(TopicOrderEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        events.recordPoints(event, routingKey);
        log.info("积分消费者处理 eventId={}，orderId={}，routingKey={}", event.id(), event.orderId(), routingKey);
    }
}
