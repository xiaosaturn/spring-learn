package org.masterh.rabbitmqdemo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class DelayOrderStore {
    private final ConcurrentHashMap<String, Order> orders = new ConcurrentHashMap<>();

    public Order create(String product) {
        Order order = new Order(UUID.randomUUID().toString(), product, Status.WAITING_PAYMENT,
                Instant.now(), null, null, null, 0);
        orders.put(order.id(), order);
        return order;
    }

    public Optional<Order> pay(String id) {
        return Optional.ofNullable(orders.computeIfPresent(id, (key, current) -> {
            if (current.status() == Status.CANCELLED) {
                throw new IllegalStateException("订单已超时取消，无法支付");
            }
            if (current.status() == Status.PAID) {
                return current;
            }
            return new Order(current.id(), current.product(), Status.PAID,
                    current.createdAt(), Instant.now(), current.checkedAt(),
                    current.deathReason(), current.deathCount());
        }));
    }

    public Optional<Order> checkTimeout(String id, String deathReason, long deathCount) {
        return Optional.ofNullable(orders.computeIfPresent(id, (key, current) -> {
            if (current.checkedAt() != null) {
                return current;
            }
            Status nextStatus = current.status() == Status.WAITING_PAYMENT
                    ? Status.CANCELLED : current.status();
            return new Order(current.id(), current.product(), nextStatus,
                    current.createdAt(), current.paidAt(), Instant.now(), deathReason, deathCount);
        }));
    }

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<Order> all() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt))
                .toList();
    }

    public enum Status {
        WAITING_PAYMENT, PAID, CANCELLED
    }

    public record Order(String id, String product, Status status, Instant createdAt,
                        Instant paidAt, Instant checkedAt, String deathReason, long deathCount) {
    }
}
