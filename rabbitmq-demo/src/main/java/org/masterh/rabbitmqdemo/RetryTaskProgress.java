package org.masterh.rabbitmqdemo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class RetryTaskProgress {
    private final ConcurrentHashMap<String, Progress> messages = new ConcurrentHashMap<>();

    public Progress beginAttempt(RetryTaskMessage message) {
        return messages.compute(message.id(), (id, previous) -> {
            if (previous != null && previous.status() == Status.SUCCEEDED) {
                return previous;
            }
            int attempt = previous == null ? 1 : previous.attempts() + 1;
            return new Progress(id, message.text(), message.failTimes(), attempt, Status.PROCESSING);
        });
    }

    public void failed(String id) {
        messages.computeIfPresent(id, (key, previous) -> previous.withStatus(
                previous.attempts() >= RetryListenerConfiguration.MAX_ATTEMPTS
                        ? Status.EXHAUSTED : Status.RETRYING));
    }

    public void succeeded(String id) {
        messages.computeIfPresent(id, (key, previous) -> previous.withStatus(Status.SUCCEEDED));
    }

    public Optional<Progress> find(String id) {
        return Optional.ofNullable(messages.get(id));
    }

    public List<Progress> all() {
        return messages.values().stream()
                .sorted(Comparator.comparing(Progress::id))
                .toList();
    }

    public enum Status {
        PROCESSING, RETRYING, SUCCEEDED, EXHAUSTED
    }

    public record Progress(String id, String text, int failTimes, int attempts, Status status) {
        private Progress withStatus(Status nextStatus) {
            return new Progress(id, text, failTimes, attempts, nextStatus);
        }
    }
}
