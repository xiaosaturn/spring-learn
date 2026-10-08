package org.masterh.rabbitmqdemo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class TopicEventLog {
    private final ConcurrentHashMap<String, Delivery> notifications = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Delivery> points = new ConcurrentHashMap<>();

    public void recordNotification(TopicOrderEvent event, String routingKey) {
        notifications.computeIfAbsent(event.id(), ignored -> delivery(event, routingKey));
    }

    public void recordPoints(TopicOrderEvent event, String routingKey) {
        points.computeIfAbsent(event.id(), ignored -> delivery(event, routingKey));
    }

    public Snapshot snapshot() {
        return new Snapshot(sorted(notifications), sorted(points));
    }

    private Delivery delivery(TopicOrderEvent event, String routingKey) {
        return new Delivery(event.id(), event.orderId(), event.eventType(), routingKey, Instant.now());
    }

    private List<Delivery> sorted(ConcurrentHashMap<String, Delivery> records) {
        return records.values().stream()
                .sorted(Comparator.comparing(Delivery::processedAt).thenComparing(Delivery::eventId))
                .toList();
    }

    public record Delivery(String eventId, String orderId, String eventType,
                           String routingKey, Instant processedAt) {
    }

    public record Snapshot(List<Delivery> notifications, List<Delivery> points) {
    }
}
