package org.masterh.rabbitmqdemo;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class TaskListener {
    private final ProcessedTasks processedTasks;

    public TaskListener(ProcessedTasks processedTasks) {
        this.processedTasks = processedTasks;
    }

    @RabbitListener(queues = RabbitTopology.TASK_QUEUE, ackMode = "MANUAL",
            autoStartup = "${lesson.task.listener-enabled:true}")
    public void handle(TaskMessage message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (message.fail()) {
            channel.basicReject(deliveryTag, false);
            return;
        }

        processedTasks.record(message);
        channel.basicAck(deliveryTag, false);
    }
}
