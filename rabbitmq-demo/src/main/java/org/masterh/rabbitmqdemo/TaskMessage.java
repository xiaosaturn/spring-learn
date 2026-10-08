package org.masterh.rabbitmqdemo;

public record TaskMessage(String id, String text, boolean fail) {
}
