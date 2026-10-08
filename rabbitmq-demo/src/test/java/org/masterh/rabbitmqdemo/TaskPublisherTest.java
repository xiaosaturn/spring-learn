package org.masterh.rabbitmqdemo;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class TaskPublisherTest {
    @Test
    void confirmedAndRoutedMessageIsAccepted() {
        TaskPublisher publisher = new TaskPublisher(template(correlation ->
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null))));

        TaskPublisher.PublishResult result = publisher.publish("发送邮件", false, RabbitTopology.TASK_KEY);

        assertTrue(result.routed());
        assertEquals(RabbitTopology.TASK_KEY, result.routingKey());
    }

    @Test
    void brokerConfirmationDoesNotMeanMessageWasRouted() {
        TaskPublisher publisher = new TaskPublisher(template(correlation -> {
            correlation.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", RabbitTopology.TASK_EXCHANGE, "task.unknown"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
        }));

        TaskPublisher.PublishResult result = publisher.publish("找不到队列", false, "task.unknown");

        assertFalse(result.routed());
    }

    @Test
    void brokerNackIsNotReportedAsAccepted() {
        TaskPublisher publisher = new TaskPublisher(template(correlation ->
                correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker nack"))));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> publisher.publish("发送邮件", false, RabbitTopology.TASK_KEY));

        assertEquals(503, exception.getStatusCode().value());
    }

    private RabbitTemplate template(Consumer<CorrelationData> response) {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            response.accept(invocation.getArgument(4));
            return null;
        }).when(template).convertAndSend(
                eq(RabbitTopology.TASK_EXCHANGE), any(String.class), any(TaskMessage.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        return template;
    }
}
