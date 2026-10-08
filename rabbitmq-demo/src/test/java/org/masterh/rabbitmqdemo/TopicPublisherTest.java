package org.masterh.rabbitmqdemo;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TopicPublisherTest {
    @Test
    void topicPublishUsesOneEventIdForPayloadMessageAndConfirmation() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(
                eq(TopicTopology.EXCHANGE), eq("order.paid"), any(TopicOrderEvent.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        TaskPublisher.PublishResult result = new TaskPublisher(template)
                .publishTopic("order-3", "paid", "order.paid");

        ArgumentCaptor<TopicOrderEvent> eventCaptor = ArgumentCaptor.forClass(TopicOrderEvent.class);
        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(eq(TopicTopology.EXCHANGE), eq("order.paid"),
                eventCaptor.capture(), processorCaptor.capture(), correlationCaptor.capture());
        TopicOrderEvent event = eventCaptor.getValue();
        Message processed = processorCaptor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));

        assertTrue(result.routed());
        assertEquals("order.paid", result.routingKey());
        assertEquals("order-3", event.orderId());
        assertEquals("paid", event.eventType());
        assertEquals(UUID.fromString(event.id()).toString(), event.id());
        assertEquals(result.id(), event.id());
        assertEquals(event.id(), correlationCaptor.getValue().getId());
        assertEquals(event.id(), processed.getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT, processed.getMessageProperties().getDeliveryMode());
    }
}
