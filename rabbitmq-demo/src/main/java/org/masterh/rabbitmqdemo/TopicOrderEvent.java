package org.masterh.rabbitmqdemo;

public record TopicOrderEvent(String id, String orderId, String eventType) {
}
