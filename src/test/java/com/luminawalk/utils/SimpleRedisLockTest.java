package com.luminawalk.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleRedisLockTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private SimpleRedisLock lock() {
        when(redis.opsForValue()).thenReturn(values);
        return new SimpleRedisLock("test", redis);
    }

    @Test
    void leaseUsesSpecifiedUnit() {
        when(values.setIfAbsent(eq("lock:test"), anyString(), eq(200L), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        assertTrue(lock().tryLock(0, 200, TimeUnit.MILLISECONDS));
    }

    @Test
    void originalApiDoesNotRetry() {
        assertFalse(lock().tryLock(10));
        verify(values, times(1)).setIfAbsent(eq("lock:test"), anyString(), eq(10L), eq(TimeUnit.SECONDS));
    }

    @Test
    void retriesUntilLockBecomesAvailable() {
        when(values.setIfAbsent(eq("lock:test"), anyString(), eq(1L), eq(TimeUnit.SECONDS)))
                .thenReturn(false, true);
        assertTrue(lock().tryLock(1, 1, TimeUnit.SECONDS));
        verify(values, times(2)).setIfAbsent(eq("lock:test"), anyString(), eq(1L), eq(TimeUnit.SECONDS));
    }

    @Test
    void interruptionStopsWaitingAndPreservesFlag() {
        Thread.currentThread().interrupt();
        try {
            assertFalse(lock().tryLock(1, 1, TimeUnit.SECONDS));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
