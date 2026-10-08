package org.masterh.rabbitmqdemo;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageConversionTest {
    @Test
    void taskMessageSurvivesJsonRoundTrip() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter("org.masterh.rabbitmqdemo");
        TaskMessage original = new TaskMessage("task-1", "发送欢迎邮件", false);

        Message wireMessage = converter.toMessage(original, new MessageProperties());

        assertEquals(original, converter.fromMessage(wireMessage));
    }
}
