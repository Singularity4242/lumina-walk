package com.luminawalk.service.impl;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.SeckillVoucher;
import com.luminawalk.entity.Voucher;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.mapper.VoucherOrderMapper;
import com.luminawalk.service.ISeckillVoucherService;
import com.luminawalk.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luminawalk.utils.RedisIdWorker;
import com.luminawalk.utils.SimpleRedisLock;
import com.luminawalk.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5 一人一单
        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);

        // 尝试获取锁
        boolean isLock = redisLock.tryLock(1200);
        if(!isLock) {
            //获取锁失败
            return Result.fail("不允许重复下单！");
        }
        try {
            // 5.1 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

            // 5.2 判断是否存在
            if (count > 0) {
                return Result.fail("用户已经购买过一次!");
            }

            // 6 扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1") // set stock = stock - 1
                    .eq("voucher_id", voucherId)  // where id = ? and stock > 0
                    .gt("stock", 0)
                    .update();
            // 扣减失败
            if (!success) {
                return Result.fail("库存不足！");
            }

            // 7 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);

            return Result.ok(orderId);
        } finally {
            redisLock.unlock();
        }


    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1 查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始");
        }

        // 3 判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 已经结束
            return Result.fail("秒杀已结束");
        }

        // 4 判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        //
        return createVoucherOrder(voucherId);
    }

}
