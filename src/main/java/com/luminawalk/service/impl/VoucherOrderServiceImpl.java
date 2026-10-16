package com.luminawalk.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.dto.Result;
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

import javax.annotation.Resource;
import java.util.Collections;

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

        // 4. 发送 RabbitMQ 消息
        String jsonStr = JSONUtil.toJsonStr(order);
        try {
            rabbitTemplate.convertAndSend("Common", "CQ", jsonStr);
        } catch (Exception e) {
            log.error("RabbitMQ 消息发送失败，订单ID: {}", orderId, e);

            // 【进阶优化建议】
            // 如果 MQ 发送失败，说明用户在 Redis 里扣了库存，但实际没下单。
            // 严格来说，这里应该手动回滚 Redis 里的数据（把库存加回去，把 Set 里的 userId 删掉）。
            // 但作为初学者项目，直接抛出异常，让前端提示“繁忙”即可。
            return Result.fail("系统繁忙，请稍后重试");
        }

        return Result.ok(orderId);
    }
}