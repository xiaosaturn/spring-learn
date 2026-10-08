package org.masterh.rabbitmqdemo;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DelayOrderListener {
    private static final Logger log = LoggerFactory.getLogger(DelayOrderListener.class);
    private final DelayOrderStore orders;

    public DelayOrderListener(DelayOrderStore orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = DelayTopology.READY_QUEUE, ackMode = "AUTO",
            autoStartup = "${lesson.delay.listener-enabled:true}")
    public void handle(DelayOrderMessage event, Message rawMessage) {
        String reason = "unknown";
        long count = 0;
        List<Map<String, ?>> deaths = rawMessage.getMessageProperties().getXDeathHeader();
        if (deaths != null) {
            for (Map<String, ?> death : deaths) {
                if (DelayTopology.WAIT_QUEUE.equals(String.valueOf(death.get("queue")))) {
                    reason = String.valueOf(death.get("reason"));
                    Object value = death.get("count");
                    count = value instanceof Number number ? number.longValue() : 0;
                    break;
                }
            }
        }

        var checked = orders.checkTimeout(event.orderId(), reason, count);
        if (checked.isEmpty()) {
            log.warn("延迟消息到达，但当前进程没有订单 {}；内存示例无法恢复重启前的订单", event.orderId());
            return;
        }
        var order = checked.get();
        log.info("订单超时检查 id={}，状态={}，死信原因={}，死信次数={}",
                order.id(), order.status(), order.deathReason(), order.deathCount());
    }
}
