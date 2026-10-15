package com.luminawalk.service;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);
}
