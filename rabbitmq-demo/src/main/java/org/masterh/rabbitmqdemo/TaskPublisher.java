package org.masterh.rabbitmqdemo;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskPublisher {
    private final RabbitTemplate rabbitTemplate;

    public TaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public PublishResult publish(String text, boolean fail, String routingKey) {
        String id = UUID.randomUUID().toString();
        TaskMessage message = new TaskMessage(id, text, fail);
        CorrelationData correlation = new CorrelationData(id);

        try {
            rabbitTemplate.convertAndSend(RabbitTopology.TASK_EXCHANGE, routingKey, message, amqpMessage -> {
                amqpMessage.getMessageProperties().setMessageId(id);
                amqpMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return amqpMessage;
            }, correlation);
        } catch (AmqpException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "无法向 RabbitMQ 发布消息", exception);
        }

        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "等待 RabbitMQ 确认时被中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未收到 RabbitMQ 发布确认", exception);
        }

        if (!confirm.ack()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ 未确认消息: " + confirm.reason());
        }

        boolean routed = correlation.getReturned() == null;
        return new PublishResult(id, routingKey, routed);
    }

    public record PublishResult(String id, String routingKey, boolean routed) {
    }
}
