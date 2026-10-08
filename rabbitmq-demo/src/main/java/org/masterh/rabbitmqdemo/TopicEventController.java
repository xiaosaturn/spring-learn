package org.masterh.rabbitmqdemo;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/lesson/topic/events")
public class TopicEventController {
    private static final Set<String> EVENT_TYPES = Set.of("created", "paid", "cancelled");
    private final TaskPublisher publisher;
    private final TopicEventLog events;

    public TopicEventController(TaskPublisher publisher, TopicEventLog events) {
        this.publisher = publisher;
        this.events = events;
    }

    @PostMapping
    public ResponseEntity<TaskPublisher.PublishResult> publish(@RequestBody PublishRequest request,
            @RequestParam(required = false) String routingKey) {
        if (request.orderId() == null || request.orderId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderId 不能为空");
        }
        if (request.eventType() == null || !EVENT_TYPES.contains(request.eventType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType 必须为 created、paid 或 cancelled");
        }
        String key = routingKey == null ? "order." + request.eventType() : routingKey;
        var result = publisher.publishTopic(request.orderId(), request.eventType(), key);
        return result.routed() ? ResponseEntity.accepted().body(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    @GetMapping
    public TopicEventLog.Snapshot snapshot() {
        return events.snapshot();
    }

    public record PublishRequest(String orderId, String eventType) {
    }
}
