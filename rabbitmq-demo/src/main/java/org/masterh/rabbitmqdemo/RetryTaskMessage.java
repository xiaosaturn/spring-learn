package org.masterh.rabbitmqdemo;

public record RetryTaskMessage(String id, String text, int failTimes) {
}
