package com.luminawalk.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.luminawalk.config.RocketMQConstants;
import com.luminawalk.dto.Result;
import com.luminawalk.dto.UserDTO;
import com.luminawalk.entity.VoucherOrder;
import com.luminawalk.utils.RedisIdWorker;
import com.luminawalk.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static com.luminawalk.utils.RedisConstants.SECKILL_ORDER_PENDING_KEY;
import static com.luminawalk.utils.RedisConstants.SECKILL_ORDER_PENDING_TTL_MINUTES;
import static com.luminawalk.utils.RedisConstants.SECKILL_STOCK_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoucherOrderMessageTest {
    private final VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
    private final RocketMQTemplate rocketMQ = mock(RocketMQTemplate.class);
    private final ObjectMapper objectMapper = spy(new ObjectMapper().registerModule(new JavaTimeModule()));
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SetOperations<String, String> sets = mock(SetOperations.class);
    private StringRedisTemplate redis;
    private long eligibility;

    @BeforeEach
    void setUp() {
        RedisIdWorker ids = mock(RedisIdWorker.class);
        when(ids.nextId("order")).thenReturn(123L);
        redis = mock(StringRedisTemplate.class, invocation ->
                "execute".equals(invocation.getMethod().getName()) ? eligibility : RETURNS_DEFAULTS.answer(invocation));
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        ReflectionTestUtils.setField(service, "redisIdWorker", ids);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "rocketMQTemplate", rocketMQ);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void sendsUnpaidOrderAndSeparateTimeoutMessage() throws Exception {
        assertTrue(service.seckillVoucher(9L).getSuccess());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(rocketMQ).syncSend(eq(RocketMQConstants.SECKILL_ORDER_DESTINATION), json.capture());
        VoucherOrder order = objectMapper.readValue(json.getValue(), VoucherOrder.class);
        assertEquals(123L, order.getId());
        assertEquals(7L, order.getUserId());
        assertEquals(9L, order.getVoucherId());
        assertEquals(VoucherOrder.STATUS_UNPAID, order.getStatus());
        assertNotNull(order.getCreateTime());
        assertEquals(order.getCreateTime(), order.getUpdateTime());
        verify(values).set(SECKILL_ORDER_PENDING_KEY + 123L, "7",
                SECKILL_ORDER_PENDING_TTL_MINUTES, TimeUnit.MINUTES);
        verify(rocketMQ).syncSendDelayTimeMills(RocketMQConstants.ORDER_TIMEOUT_DESTINATION, "123", 900_000L);
    }

    @Test
    void rejectedPurchaseSendsNoMessages() {
        eligibility = 1;
        assertFalse(service.seckillVoucher(9L).getSuccess());
        verifyNoInteractions(rocketMQ);
    }

    @Test
    void failedOrderSendRollsBackReservationAndDoesNotSendTimeoutMessage() {
        doThrow(new IllegalStateException("send failed")).when(rocketMQ)
                .syncSend(eq(RocketMQConstants.SECKILL_ORDER_DESTINATION), anyString());
        assertFalse(service.seckillVoucher(9L).getSuccess());
        verify(redis).delete(SECKILL_ORDER_PENDING_KEY + 123L);
        verify(values).increment(SECKILL_STOCK_KEY + 9L);
        verify(sets).remove("seckill:order:9", "7");
        verify(rocketMQ, never()).syncSendDelayTimeMills(anyString(), anyString(), anyLong());
    }

    @Test
    void timeoutSendFailurePreservesUpstreamSuccessResponse() {
        doThrow(new IllegalStateException("send failed")).when(rocketMQ)
                .syncSendDelayTimeMills(eq(RocketMQConstants.ORDER_TIMEOUT_DESTINATION), anyString(), anyLong());
        Result result = service.seckillVoucher(9L);
        assertTrue(result.getSuccess());
        assertEquals(123L, result.getData());
        verify(redis, never()).delete(anyString());
        verify(values, never()).increment(anyString());
    }

    @Test
    void duplicatePurchaseSendsNoMessages() {
        eligibility = 2;
        assertFalse(service.seckillVoucher(9L).getSuccess());
        verifyNoInteractions(rocketMQ);
    }

    @Test
    void serializationFailureRollsBackReservationWithoutSendingMessages() throws Exception {
        doThrow(new JsonProcessingException("serialization failed") { })
                .when(objectMapper).writeValueAsString(any(VoucherOrder.class));

        assertFalse(service.seckillVoucher(9L).getSuccess());

        verify(redis).delete(SECKILL_ORDER_PENDING_KEY + 123L);
        verify(values).increment(SECKILL_STOCK_KEY + 9L);
        verify(sets).remove("seckill:order:9", "7");
        verifyNoInteractions(rocketMQ);
    }
}
