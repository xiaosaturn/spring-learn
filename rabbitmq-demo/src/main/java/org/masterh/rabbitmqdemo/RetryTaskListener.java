package org.masterh.rabbitmqdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RetryTaskListener {
    private static final Logger log = LoggerFactory.getLogger(RetryTaskListener.class);
    private final RetryTaskProgress progress;

    public RetryTaskListener(RetryTaskProgress progress) {
        this.progress = progress;
    }

    @RabbitListener(queues = RetryTopology.TASK_QUEUE, containerFactory = "retryListenerContainerFactory")
    public void handle(RetryTaskMessage message) {
        RetryTaskProgress.Progress current = progress.beginAttempt(message);
        if (current.status() == RetryTaskProgress.Status.SUCCEEDED) {
            return;
        }

        log.info("重试练习 id={}，第 {} 次处理，前 {} 次模拟失败",
                message.id(), current.attempts(), message.failTimes());
        if (current.attempts() <= message.failTimes()) {
            progress.failed(message.id());
            throw new IllegalStateException("模拟第 " + current.attempts() + " 次处理失败");
        }
        progress.succeeded(message.id());
    }
}
