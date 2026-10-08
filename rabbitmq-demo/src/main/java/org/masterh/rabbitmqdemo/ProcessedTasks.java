package org.masterh.rabbitmqdemo;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ProcessedTasks {
    private final ConcurrentHashMap<String, TaskMessage> messages = new ConcurrentHashMap<>();

    public void record(TaskMessage message) {
        messages.putIfAbsent(message.id(), message);
    }

    public List<TaskMessage> all() {
        return messages.values().stream()
                .sorted(Comparator.comparing(TaskMessage::id))
                .toList();
    }
}
