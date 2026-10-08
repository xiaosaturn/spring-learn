package org.masterh.rabbitmqdemo;

import java.util.List;

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
@RequestMapping("/lesson/messages")
public class TaskController {
    private final TaskPublisher publisher;
    private final ProcessedTasks processedTasks;

    public TaskController(TaskPublisher publisher, ProcessedTasks processedTasks) {
        this.publisher = publisher;
        this.processedTasks = processedTasks;
    }

    @PostMapping
    public ResponseEntity<TaskPublisher.PublishResult> publish(
            @RequestBody PublishRequest request,
            @RequestParam(defaultValue = RabbitTopology.TASK_KEY) String routingKey) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text 不能为空");
        }

        TaskPublisher.PublishResult result = publisher.publish(request.text(), request.fail(), routingKey);
        if (!result.routed()) {
            return ResponseEntity.unprocessableEntity().body(result);
        }
        return ResponseEntity.accepted().body(result);
    }

    @GetMapping("/processed")
    public List<TaskMessage> processed() {
        return processedTasks.all();
    }

    public record PublishRequest(String text, boolean fail) {
    }
}
