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
@RequestMapping("/lesson/delay/orders")
public class DelayOrderController {
    private final TaskPublisher publisher;
    private final DelayOrderStore orders;

    public DelayOrderController(TaskPublisher publisher, DelayOrderStore orders) {
        this.publisher = publisher;
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<DelayOrderStore.Order> create(@RequestBody CreateRequest request) {
        if (request.product() == null || request.product().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product 不能为空");
        }
        var order = orders.create(request.product());
        var result = publisher.publishDelay(new DelayOrderMessage(order.id()));
        if (!result.routed()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "订单检查消息未路由到等待队列");
        }
        return ResponseEntity.accepted().body(order);
    }

    @GetMapping
    public List<DelayOrderStore.Order> all() {
        return orders.all();
    }

    @GetMapping("/{id}")
    public DelayOrderStore.Order find(@PathVariable String id) {
        return orders.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "当前进程没有这个订单"));
    }

    @PostMapping("/{id}/pay")
    public DelayOrderStore.Order pay(@PathVariable String id) {
        try {
            return orders.pay(id).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "当前进程没有这个订单"));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    public record CreateRequest(String product) {
    }
}
