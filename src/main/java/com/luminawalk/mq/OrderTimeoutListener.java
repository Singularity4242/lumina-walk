package com.luminawalk.mq;


import com.luminawalk.config.QueueConfig;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.service.ISeckillVoucherService;
import com.luminawalk.service.IVoucherOrderService;
import com.luminawalk.utils.SimpleRedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static com.luminawalk.utils.RedisConstants.ORDER_TIMEOUT_LOCK_PREFIX;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final IVoucherOrderService voucherOrderService;
    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = QueueConfig.ORDER_TIMEOUT_QUEUE)
    public void handleOrderTimeout (String orderIdStr) {
        log.info("收到订单超时消息：订单ID={}", orderIdStr);

        try {
            Long orderId = Long.parseLong(orderIdStr);

            String lockKey = ORDER_TIMEOUT_LOCK_PREFIX + orderId;
            SimpleRedisLock lock = new SimpleRedisLock(lockKey, stringRedisTemplate);

            boolean isLock = lock.tryLock(0, 5, TimeUnit.SECONDS);
            if (!isLock) {
                log.warn("获取订单超时处理锁失败，订单ID={}", orderId);
                return;
            }

            try {
                VoucherOrder order = voucherOrderService.getById(orderId);
                if (order == null) {
                    log.warn("订单不存在，订单ID={}", orderId);
                    return;
                }

                if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
                    log.info("订单状态不是未支付，无需关闭，订单ID={}, 状态={}", orderId, order.getStatus());
                    return;
                }

                order.setStatus(VoucherOrder.STATUS_CANCELLED);
                voucherOrderService.updateById(order);

                Long voucherId = order.getVoucherId();
                seckillVoucherService.update()
                        .setSql("stock = stock + 1")
                        .eq("voucher_id", voucherId)
                        .update();

                log.info("订单超时已关闭，订单ID={}", orderId);
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            log.error("订单超时处理异常，订单ID={}", orderIdStr, e);
        }
    }
}
