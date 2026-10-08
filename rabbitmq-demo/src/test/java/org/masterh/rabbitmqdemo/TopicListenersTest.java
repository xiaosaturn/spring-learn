package org.masterh.rabbitmqdemo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TopicListenersTest {
    @Test
    void sameEventIsRecordedIndependentlyByBothBusinesses() {
        TopicEventLog events = new TopicEventLog();
        TopicNotificationListener notification = new TopicNotificationListener(events);
        TopicPointsListener points = new TopicPointsListener(events);
        TopicOrderEvent event = new TopicOrderEvent("event-1", "order-1", "paid");

        notification.handle(event, "order.paid");
        points.handle(event, "order.paid");

        TopicEventLog.Snapshot snapshot = events.snapshot();
        assertEquals(1, snapshot.notifications().size());
        assertEquals(1, snapshot.points().size());
        for (TopicEventLog.Delivery delivery : new TopicEventLog.Delivery[]{
                snapshot.notifications().get(0), snapshot.points().get(0)}) {
            assertEquals(event.id(), delivery.eventId());
            assertEquals(event.orderId(), delivery.orderId());
            assertEquals(event.eventType(), delivery.eventType());
            assertEquals("order.paid", delivery.routingKey());
            assertNotNull(delivery.processedAt());
        }
    }

    @Test
    void redeliveryDoesNotRecordEitherBusinessAgainOrReplaceProcessingTime() {
        TopicEventLog events = new TopicEventLog();
        TopicNotificationListener notification = new TopicNotificationListener(events);
        TopicPointsListener points = new TopicPointsListener(events);
        TopicOrderEvent event = new TopicOrderEvent("event-2", "order-2", "paid");
        notification.handle(event, "order.paid");
        points.handle(event, "order.paid");
        TopicEventLog.Snapshot first = events.snapshot();

        notification.handle(event, "order.paid");
        points.handle(event, "order.paid");

        TopicEventLog.Snapshot redelivered = events.snapshot();
        assertEquals(1, redelivered.notifications().size());
        assertEquals(1, redelivered.points().size());
        assertEquals(first, redelivered);
        assertSame(first.notifications().get(0), redelivered.notifications().get(0));
        assertSame(first.points().get(0), redelivered.points().get(0));
    }
}
