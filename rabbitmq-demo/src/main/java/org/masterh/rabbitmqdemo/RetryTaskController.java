package org.masterh.rabbitmqdemo;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/lesson/retry/messages")
public class RetryTaskController {
    private final TaskPublisher publisher;
    private final RetryTaskProgress progress;

    public RetryTaskController(TaskPublisher publisher, RetryTaskProgress progress) {
        this.publisher = publisher;
        this.progress = progress;
    }

    @PostMapping
    public ResponseEntity<TaskPublisher.PublishResult> publish(@RequestBody PublishRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text 不能为空");
        }
        if (request.failTimes() < 0 || request.failTimes() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failTimes 必须在 0 到 10 之间");
        }
        TaskPublisher.PublishResult result = publisher.publishRetry(request.text(), request.failTimes());
        return result.routed() ? ResponseEntity.accepted().body(result)
                : ResponseEntity.unprocessableEntity().body(result);
    }

    @GetMapping
    public List<RetryTaskProgress.Progress> all() {
        return progress.all();
    }

    @GetMapping("/{id}")
    public RetryTaskProgress.Progress find(@PathVariable String id) {
        return progress.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "消费者尚未记录这条消息"));
    }

    public record PublishRequest(String text, int failTimes) {
    }
}
