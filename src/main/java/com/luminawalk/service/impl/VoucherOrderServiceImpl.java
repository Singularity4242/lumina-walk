package com.luminawalk.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.dto.Result;
import com.luminawalk.config.QueueConfig;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.mapper.VoucherOrderMapper;
import com.luminawalk.service.IVoucherOrderService;
import com.luminawalk.utils.RedisIdWorker;
import com.luminawalk.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.time.LocalDateTime;

import static com.luminawalk.utils.RedisConstants.ORDER_PAYMENT_LOCK_PREFIX;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 1. 执行 Lua 脚本
        // 作用：只在 Redis 层面判断资格和预扣减库存
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        int r = result.intValue();
        // 2. 判断结果
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 3. 封装订单消息
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(VoucherOrder.STATUS_UNPAID);
        LocalDateTime now = LocalDateTime.now();
        order.setCreateTime(now);
        order.setUpdateTime(now);

        // 4. 发送 RabbitMQ 消息保存订单
        String jsonStr = JSONUtil.toJsonStr(order);
        try {
            rabbitTemplate.convertAndSend("Common", "CQ", jsonStr);
        } catch (Exception e) {
            log.error("RabbitMQ 消息发送失败，订单ID: {}", orderId, e);
            return Result.fail("系统繁忙，请稍后重试");
        }

        // 5. 发送超时检查消息；延迟队列中的消息 15 分钟后进入超时队列。
        // 当前上游提交尚未提供超时队列消费者，发送消息本身不会关闭订单。
        try {
            rabbitTemplate.convertAndSend(
                    QueueConfig.ORDER_DELAY_EXCHANGE,
                    QueueConfig.ORDER_DELAY_ROUTING_KEY,
                    String.valueOf(orderId));
            log.info("订单超时检查消息已发送，订单ID={}", orderId);
        } catch (Exception e) {
            log.error("订单超时检查消息发送失败，订单ID: {}", orderId, e);
        }

        return Result.ok(orderId);
    }
}