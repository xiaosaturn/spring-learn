package org.masterh.rabbitmqdemo;

import java.util.List;
import java.util.Map;

import com.rabbitmq.client.impl.LongStringHelper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DelayOrderListenerTest {
    @Test
    void readsDeathMetadataAndPreservesFirstCheckOnRepeatedDelivery() {
        DelayOrderStore orders = new DelayOrderStore();
        var created = orders.create("学习资料");
        DelayOrderListener listener = new DelayOrderListener(orders);
        DelayOrderMessage event = new DelayOrderMessage(created.id());
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-death", List.of(Map.of(
                "queue", LongStringHelper.asLongString(DelayTopology.WAIT_QUEUE),
                "reason", LongStringHelper.asLongString("expired"),
                "count", 1L)));
        Message raw = new Message(new byte[0], properties);

        listener.handle(event, raw);
        var firstCheck = orders.find(created.id()).orElseThrow();
        listener.handle(event, raw);
        var repeatedCheck = orders.find(created.id()).orElseThrow();

        assertEquals(DelayOrderStore.Status.CANCELLED, firstCheck.status());
        assertEquals("expired", firstCheck.deathReason());
        assertEquals(1L, firstCheck.deathCount());
        assertNotNull(firstCheck.checkedAt());
        assertEquals(firstCheck.checkedAt(), repeatedCheck.checkedAt());
        assertSame(firstCheck, repeatedCheck);
    }
}
