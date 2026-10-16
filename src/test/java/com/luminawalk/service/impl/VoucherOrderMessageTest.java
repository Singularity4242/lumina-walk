package com.luminawalk.service.impl;

import cn.hutool.json.JSONUtil;
import com.luminawalk.config.QueueConfig;
import com.luminawalk.dto.Result;
import com.luminawalk.dto.UserDTO;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.utils.RedisIdWorker;
import com.luminawalk.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherOrderMessageTest {
    private final VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private long eligibility;

    @BeforeEach
    void setUp() {
        RedisIdWorker ids = mock(RedisIdWorker.class);
        when(ids.nextId("order")).thenReturn(123L);
        StringRedisTemplate redis = mock(StringRedisTemplate.class, invocation ->
                "execute".equals(invocation.getMethod().getName()) ? eligibility : RETURNS_DEFAULTS.answer(invocation));
        ReflectionTestUtils.setField(service, "redisIdWorker", ids);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void sendsUnpaidOrderAndSeparateTimeoutMessage() {
        assertTrue(service.seckillVoucher(9L).getSuccess());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(rabbit).convertAndSend(eq("Common"), eq("CQ"), json.capture());
        VoucherOrder order = JSONUtil.toBean(json.getValue(), VoucherOrder.class);
        assertEquals(123L, order.getId());
        assertEquals(7L, order.getUserId());
        assertEquals(9L, order.getVoucherId());
        assertEquals(VoucherOrder.STATUS_UNPAID, order.getStatus());
        assertNotNull(order.getCreateTime());
        assertEquals(order.getCreateTime(), order.getUpdateTime());
        verify(rabbit).convertAndSend(QueueConfig.ORDER_DELAY_EXCHANGE,
                QueueConfig.ORDER_DELAY_ROUTING_KEY, "123");
    }

    @Test
    void rejectedPurchaseSendsNoMessages() {
        eligibility = 1;
        assertFalse(service.seckillVoucher(9L).getSuccess());
        verifyNoInteractions(rabbit);
    }

    @Test
    void failedOrderSendDoesNotSendTimeoutMessage() {
        doThrow(new IllegalStateException("send failed")).when(rabbit)
                .convertAndSend(eq("Common"), eq("CQ"), anyString());
        assertFalse(service.seckillVoucher(9L).getSuccess());
        verify(rabbit, never()).convertAndSend(eq(QueueConfig.ORDER_DELAY_EXCHANGE), anyString(), anyString());
    }

    @Test
    void timeoutSendFailurePreservesUpstreamSuccessResponse() {
        doThrow(new IllegalStateException("send failed")).when(rabbit)
                .convertAndSend(eq(QueueConfig.ORDER_DELAY_EXCHANGE), anyString(), anyString());
        Result result = service.seckillVoucher(9L);
        assertTrue(result.getSuccess());
        assertEquals(123L, result.getData());
    }

    @Test
    void delayQueueExpiresIntoTimeoutQueueAfterFifteenMinutes() {
        QueueConfig config = new QueueConfig();
        Map<String, Object> args = config.orderDelayQueue().getArguments();
        assertEquals(900000L, args.get("x-message-ttl"));
        assertEquals(QueueConfig.ORDER_TIMEOUT_EXCHANGE, args.get("x-dead-letter-exchange"));
        assertEquals(QueueConfig.ORDER_TIMEOUT_ROUTING_KEY, args.get("x-dead-letter-routing-key"));
        assertEquals(QueueConfig.ORDER_DELAY_ROUTING_KEY,
                config.bindingOrderDelayQueueToOrderDelayExchange(config.orderDelayQueue(),
                        config.orderDelayExchange()).getRoutingKey());
        assertEquals(QueueConfig.ORDER_TIMEOUT_QUEUE,
                config.bindingOrderTimeoutQueueToOrderTimeoutExchange(config.orderTimeoutQueue(),
                        config.orderTimeoutExchange()).getDestination());
    }
}
