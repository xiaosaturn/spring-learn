package org.masterh.rabbitmqdemo;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskListenerTest {
    @Test
    void acknowledgesProcessedMessage() throws IOException {
        ProcessedTasks processed = new ProcessedTasks();
        Channel channel = mock(Channel.class);
        TaskMessage message = new TaskMessage("task-1", "发送邮件", false);

        new TaskListener(processed).handle(message, channel, 7L);

        assertEquals(message, processed.all().get(0));
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicReject(7L, false);
    }

    @Test
    void rejectsFailedMessageWithoutRequeueing() throws IOException {
        ProcessedTasks processed = new ProcessedTasks();
        Channel channel = mock(Channel.class);

        new TaskListener(processed).handle(new TaskMessage("task-2", "模拟失败", true), channel, 8L);

        assertTrue(processed.all().isEmpty());
        verify(channel).basicReject(8L, false);
        verify(channel, never()).basicAck(8L, false);
    }

    @Test
    void repeatedDeliveryKeepsOneProcessedResult() throws IOException {
        ProcessedTasks processed = new ProcessedTasks();
        Channel channel = mock(Channel.class);
        TaskMessage message = new TaskMessage("task-3", "重复投递", false);
        TaskListener listener = new TaskListener(processed);

        listener.handle(message, channel, 9L);
        listener.handle(message, channel, 10L);

        assertEquals(1, processed.all().size());
        verify(channel).basicAck(9L, false);
        verify(channel).basicAck(10L, false);
    }
}
