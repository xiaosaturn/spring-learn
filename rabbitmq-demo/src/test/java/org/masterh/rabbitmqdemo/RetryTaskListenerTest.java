package org.masterh.rabbitmqdemo;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetryTaskListenerTest {
    @Test
    void transientFailureSucceedsOnThirdAttempt() throws Throwable {
        RetryTaskProgress progress = new RetryTaskProgress();
        RetryTaskListener listener = new RetryTaskListener(progress);
        RetryTaskMessage message = new RetryTaskMessage("retry-transient", "前两次失败", 2);

        RetryListenerConfiguration.buildInterceptor().invoke(delivery(listener, message));

        RetryTaskProgress.Progress result = progress.find(message.id()).orElseThrow();
        assertEquals(3, result.attempts());
        assertEquals(RetryTaskProgress.Status.SUCCEEDED, result.status());
    }

    @Test
    void persistentFailureIsRejectedAfterThreeAttempts() throws Throwable {
        RetryTaskProgress progress = new RetryTaskProgress();
        RetryTaskListener listener = new RetryTaskListener(progress);
        RetryTaskMessage message = new RetryTaskMessage("retry-exhausted", "始终失败", 3);
        MethodInvocation invocation = delivery(listener, message);

        ListenerExecutionFailedException exception = assertThrows(ListenerExecutionFailedException.class,
                () -> RetryListenerConfiguration.buildInterceptor().invoke(invocation));

        AmqpRejectAndDontRequeueException rejection = assertInstanceOf(
                AmqpRejectAndDontRequeueException.class, exception.getCause());
        assertInstanceOf(IllegalStateException.class, rejection.getCause());
        RetryTaskProgress.Progress result = progress.find(message.id()).orElseThrow();
        assertEquals(3, result.attempts());
        assertEquals(RetryTaskProgress.Status.EXHAUSTED, result.status());
    }

    @Test
    void successfulDuplicateDoesNotRepeatBusinessAttempt() throws Throwable {
        RetryTaskProgress progress = new RetryTaskProgress();
        RetryTaskListener listener = new RetryTaskListener(progress);
        RetryTaskMessage message = new RetryTaskMessage("retry-duplicate", "重复投递", 0);
        MethodInterceptor interceptor = RetryListenerConfiguration.buildInterceptor();

        interceptor.invoke(delivery(listener, message));
        interceptor.invoke(delivery(listener, message));

        RetryTaskProgress.Progress result = progress.find(message.id()).orElseThrow();
        assertEquals(1, result.attempts());
        assertEquals(RetryTaskProgress.Status.SUCCEEDED, result.status());
        assertEquals(1, progress.all().size());
    }

    private MethodInvocation delivery(RetryTaskListener listener, RetryTaskMessage task) throws Throwable {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(task.id());
        Message rawMessage = new Message(new byte[0], properties);
        MethodInvocation invocation = mock(MethodInvocation.class);
        // The interceptor wraps the container call (Channel, raw Message), before conversion to the task record.
        when(invocation.getArguments()).thenReturn(new Object[] {null, rawMessage});
        doAnswer(ignored -> {
            listener.handle(task);
            return null;
        }).when(invocation).proceed();
        return invocation;
    }
}
