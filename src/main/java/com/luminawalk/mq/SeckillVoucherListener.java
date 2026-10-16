package com.luminawalk.mq;


import cn.hutool.json.JSONUtil;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.service.impl.SeckillVoucherServiceImpl;
import com.luminawalk.service.impl.VoucherOrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel; // Channel 是这个包的
import org.springframework.amqp.core.Message; // Message 是这个包的
import javax.annotation.Resource;


@Component
@RequiredArgsConstructor
@Slf4j
public class SeckillVoucherListener {
    @Resource
    SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    VoucherOrderServiceImpl voucherOrderService;

    /**
     * 普通队列消费者：监听队列 "CQ"
     *
     * 消息从普通队列 "CQ" 进入后进行转换处理，保存订单，同时数据库秒杀库存减一
     *
     * @param message RabbitMQ消息
     * @param channel 消息通道
     * @throws Exception 异常处理
     */
    @RabbitListener(queues = "CQ")
    public void receivedC(Message message, Channel channel) throws Exception {
        String msg = new String(message.getBody());
        log.info("普通队列:");
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        log.info(voucherOrder.toString());
        voucherOrderService.save(voucherOrder);  // 保存订单到数据库

        // 秒杀业务：库存减一操作
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0)             // where voucher_id = ? and stock > 0
                .update();
    }

    /**
     * 死信队列消费者：监听队列 "DLQ"
     *
     * 消息从死信队列 "DLQ" 进入后进行相同的处理，
     * 适用于消息因过期或其它原因进入死信队列时的处理逻辑
     *
     * @param message RabbitMQ消息
     * @throws Exception 异常处理
     */
    @RabbitListener(queues = "DLQ")
    public void receivedDLQ(Message message) throws Exception {
        log.info("死信队列:");
        String msg = new String(message.getBody());
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        log.info(voucherOrder.toString());
        voucherOrderService.save(voucherOrder);  // 保存订单到数据库

        // 秒杀业务：库存减一操作
        Long voucherId = voucherOrder.getVoucherId();
        seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0)             // where voucher_id = ? and stock > 0
                .update();
    }
}
