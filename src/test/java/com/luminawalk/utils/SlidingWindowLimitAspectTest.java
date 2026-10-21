package com.luminawalk.utils;

import com.luminawalk.config.WebExceptionAdvice;
import com.luminawalk.dto.Result;
import com.luminawalk.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SlidingWindowLimitAspectTest {
    private final List<String> keys = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private int rejectCall;
    private LimitedController controller;
    private RedissonClient redisson;

    @BeforeEach
    void setUp() {
        RScript script = mock(RScript.class, invocation -> {
            if ("eval".equals(invocation.getMethod().getName())) {
                keys.add(((List<?>) invocation.getArgument(3)).get(0).toString());
                return calls.incrementAndGet() == rejectCall ? 1L : 0L;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
        redisson = mock(RedissonClient.class);
        when(redisson.getScript(StringCodec.INSTANCE)).thenReturn(script);
        SlidingWindowLimitAspect aspect = new SlidingWindowLimitAspect();
        ReflectionTestUtils.setField(aspect, "redissonClient", redisson);
        AspectJProxyFactory factory = new AspectJProxyFactory(new LimitedController());
        factory.addAspect(aspect);
        controller = factory.getProxy();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        login(1L);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void classAnnotationRejectsBeforeBusinessMethod() {
        rejectCall = 1;
        assertThrows(RateLimitExceededException.class, () -> controller.read());
        assertEquals(1, calls.get());
    }

    @Test
    void globalLimitIsSharedAcrossUsersAndMethods() {
        controller.read();
        login(2L);
        controller.buy(9L);
        assertEquals(keys.get(0), keys.get(1));
        assertEquals(3, calls.get());
    }

    @Test
    void userLimitAlsoRejectsAfterGlobalCheck() {
        rejectCall = 2;
        assertThrows(RateLimitExceededException.class, () -> controller.buy(9L));
        assertEquals(2, calls.get());
    }

    @Test
    void zeroThresholdReachesLuaInsteadOfThrowingValidationError() {
        // 确认真实注解经过 checkLimit 时不再因阈值为 0 报错，也没有提前跳过 Lua。
        // Lua 对 0 的实际处理由真实 Redis 集成测试验证。
        assertTrue(controller.unlimited().getSuccess());
        assertEquals(2, calls.get(), "类级检查和方法级检查都应执行");
    }

    @Test
    void userLimitSeparatesUsersAndVouchers() {
        controller.buy(9L);
        controller.buy(10L);
        login(2L);
        controller.buy(9L);
        assertNotEquals(keys.get(1), keys.get(3));
        assertNotEquals(keys.get(1), keys.get(5));
        assertTrue(keys.get(1).endsWith(":9"));
    }

    @Test
    void anonymousRequestCanUseGlobalLimit() {
        UserHolder.removeUser();
        assertTrue(controller.read().getSuccess());
        assertEquals(1, calls.get());
    }

    @Test
    void nonWebCallSkipsRedis() {
        RequestContextHolder.resetRequestAttributes();
        assertTrue(controller.read().getSuccess());
        verify(redisson, never()).getScript(any(org.redisson.client.codec.Codec.class));
    }

    @Test
    void redisFailureKeepsUpstreamFailOpenPolicy() {
        when(redisson.getScript(StringCodec.INSTANCE)).thenThrow(new IllegalStateException("Redis unavailable"));
        assertTrue(controller.read().getSuccess());
    }

    @Test
    void limitResponseUses429AndExistingResultFormat() throws Exception {
        rejectCall = 1;
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new WebExceptionAdvice()).build()
                .perform(get("/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMsg").value("请求过于频繁，请稍后重试"));
    }

    private void login(long id) {
        UserDTO user = new UserDTO();
        user.setId(id);
        UserHolder.saveUser(user);
    }

    @RestController
    @SlidingWindowLimit(globalLimit = true)
    public static class LimitedController {
        @GetMapping("/limited")
        public Result read() {
            return Result.ok();
        }

        @SlidingWindowLimit(keySpEL = "#voucherId", timeUnit = TimeUnit.MINUTES, maxRequests = 1)
        public Result buy(Long voucherId) {
            return Result.ok();
        }

        @SlidingWindowLimit(globalLimit = true, maxRequests = 0)
        public Result unlimited() {
            return Result.ok();
        }
    }
}
