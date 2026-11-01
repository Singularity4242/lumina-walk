package com.luminawalk.service;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherService extends IService<Voucher> {
    Result queryVoucherOfShop(Long shopId);

    Result queryOffers(Integer current);

    void addSeckillVoucher(Voucher voucher);
}
