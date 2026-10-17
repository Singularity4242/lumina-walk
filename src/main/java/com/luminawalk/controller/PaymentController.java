package com.luminawalk.controller;

import com.luminawalk.dto.Result;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.service.IPaymentService;
import com.luminawalk.service.IVoucherOrderService;
import com.luminawalk.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final IPaymentService paymentService;
    private final IVoucherOrderService voucherOrderService;

    /**
     * 创建支付订单
     * @param orderId 订单ID
     * @param payType 支付方式：1-余额支付，2-支付宝，3-微信
     * @return 支付结果
     */
    @PostMapping("/create")
    public Result createPayment(@RequestParam("orderId") Long orderId,
                                @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
            return Result.fail("订单状态不正确");
        }
        log.info("创建支付订单成功");
        return paymentService.createPayment(orderId, payType);
    }

    /**
     * 模拟支付（用于测试）
     * @param orderId 订单ID
     * @return 支付结果
     */
    @PostMapping("/simulate/{id}")
    public Result simulatePayment(@PathVariable("id") Long orderId) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
            return Result.fail("订单状态不正确");
        }

        return paymentService.simulatePaymentCallback(orderId);
    }

    /**
     * 微信支付
     * @param orderId 订单ID
     * @return 支付结果（返回支付链接等）
     */
    @PostMapping("/wechat/{id}")
    public Result wechatPay(@PathVariable("id") Long orderId) {
        return createPaymentAndReturnUrl(orderId, VoucherOrder.PAY_TYPE_WECHAT);
    }

    /**
     * 支付宝支付
     * @param orderId 订单ID
     * @return 支付结果（返回支付链接等）
     */
    @PostMapping("/alipay/{id}")
    public Result alipay(@PathVariable("id") Long orderId) {
        return createPaymentAndReturnUrl(orderId, VoucherOrder.PAY_TYPE_ALIPAY);
    }

    private Result createPaymentAndReturnUrl(Long orderId, Integer payType) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
            return Result.fail("订单状态不正确");
        }

        paymentService.createPayment(orderId, payType);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("payType", payType);

        if (payType.equals(VoucherOrder.PAY_TYPE_ALIPAY)) {
            result.put("payUrl", "alipay://...?orderId=" + orderId);
        } else if (payType.equals(VoucherOrder.PAY_TYPE_WECHAT)) {
            result.put("payUrl", "weixin://wxpay/bizpayurl?pr=" + orderId);
        }

        return Result.ok(result);
    }

    /**
     * 微信支付回调（实际项目中应由微信服务器调用）
     * @param orderId 订单ID
     * @return 处理结果
     */
    @PostMapping("/callback/wechat/{id}")
    public String wechatPayCallback(@PathVariable("id") Long orderId) {
        Result result = paymentService.wechatPayCallback(orderId);
        return result.getSuccess() ? "SUCCESS" : "FAIL";
    }

    /**
     * 支付宝支付回调（实际项目中应由支付宝服务器调用）
     * @param orderId 订单ID
     * @return 处理结果
     */
    @PostMapping("/callback/alipay/{id}")
    public String alipayCallback(@PathVariable("id") Long orderId) {
        Result result = paymentService.alipayCallback(orderId);
        return result.getSuccess() ? "success" : "fail";
    }

    /**
     * 检查支付状态
     * @param orderId 订单ID
     * @return 支付状态
     */
    @GetMapping("/status/{id}")
    public Result getPaymentStatus(@PathVariable("id") Long orderId) {
        Long userId = UserHolder.getUser().getId();

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        if (!order.getUserId().equals(userId)) {
            return Result.fail("订单不属于当前用户");
        }

        return paymentService.checkPaymentStatus(orderId);
    }

}
