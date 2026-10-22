package com.luminawalk.service;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result buyVoucher(Long voucherId);

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    void closeTimeoutOrder(Long orderId);

}
