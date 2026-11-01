package com.luminawalk.service.impl;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.SeckillVoucher;
import com.luminawalk.entity.Voucher;
import com.luminawalk.mapper.VoucherMapper;
import com.luminawalk.service.ISeckillVoucherService;
import com.luminawalk.service.IVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

import static com.luminawalk.utils.RedisConstants.SECKILL_STOCK_KEY;
/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private static final int OFFERS_PAGE_SIZE = 12;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 秒杀库存保存到redis
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }

    @Override
    public Result queryOffers(Integer current) {
        if (current == null || current < 1) {
            return Result.fail("页码必须大于等于1");
        }
        long offset = (current.longValue() - 1) * OFFERS_PAGE_SIZE;
        List<Voucher> vouchers = getBaseMapper().queryOffers(offset, OFFERS_PAGE_SIZE + 1);
        boolean hasMore = vouchers.size() > OFFERS_PAGE_SIZE;
        return Result.ok(Map.of("records", hasMore ? vouchers.subList(0, OFFERS_PAGE_SIZE) : vouchers,
                "hasMore", hasMore));
    }
}
