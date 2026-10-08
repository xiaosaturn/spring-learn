package org.masterh.rabbitmqdemo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelayOrderStoreTest {
    @Test
    void cancelsUnpaidOrderWhenTimeoutCheckArrives() {
        DelayOrderStore orders = new DelayOrderStore();
        var created = orders.create("学习资料");

        var checked = orders.checkTimeout(created.id(), "expired", 1).orElseThrow();

        assertEquals(DelayOrderStore.Status.CANCELLED, checked.status());
        assertEquals(created.createdAt(), checked.createdAt());
        assertNull(checked.paidAt());
        assertNotNull(checked.checkedAt());
        assertEquals("expired", checked.deathReason());
        assertEquals(1L, checked.deathCount());
    }

    @Test
    void keepsPaidOrderWhenTimeoutCheckArrives() {
        DelayOrderStore orders = new DelayOrderStore();
        var created = orders.create("学习资料");
        var paid = orders.pay(created.id()).orElseThrow();

        var checked = orders.checkTimeout(created.id(), "expired", 1).orElseThrow();

        assertEquals(DelayOrderStore.Status.PAID, checked.status());
        assertNotNull(paid.paidAt());
        assertEquals(paid.paidAt(), checked.paidAt());
        assertNotNull(checked.checkedAt());
    }

    @Test
    void refusesPaymentAfterCancellationAndKeepsCancelledOrder() {
        DelayOrderStore orders = new DelayOrderStore();
        var created = orders.create("学习资料");
        var cancelled = orders.checkTimeout(created.id(), "expired", 1).orElseThrow();

        assertThrows(IllegalStateException.class, () -> orders.pay(created.id()));

        assertEquals(cancelled, orders.find(created.id()).orElseThrow());
        assertEquals(DelayOrderStore.Status.CANCELLED, cancelled.status());
    }
}
