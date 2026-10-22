package com.luminawalk.controller;


import com.luminawalk.dto.Result;
import com.luminawalk.utils.SlidingWindowLimit;
import com.luminawalk.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/voucher-order")
@SlidingWindowLimit(globalLimit = true)
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("/buy/{id}")
    public Result buyVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.buyVoucher(voucherId);
    }

    /**
     * 抢购秒杀券 - 双重限流保护
     * 第一层：全局限流，保护系统不被过载
     * 第二层：用户级限流，防止单用户恶意刷单
     * @param voucherId 优惠券ID
     * @return 订单结果
     */
    // 全局限流之外，同一用户对同一活动券每分钟最多请求一次。
    @SlidingWindowLimit(keySpEL = "#voucherId", windowSize = 1,
            timeUnit = TimeUnit.MINUTES, maxRequests = 1)


    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
