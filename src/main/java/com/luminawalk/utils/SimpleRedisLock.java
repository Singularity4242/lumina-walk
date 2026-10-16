package com.luminawalk.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        return tryLock(0, timeoutSec, TimeUnit.SECONDS);
    }

    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        long waitNanos = unit.toNanos(waitTime);
        long startTime = System.nanoTime();
        while (true) {
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + name, threadId, leaseTime, unit);
            if (Boolean.TRUE.equals(success)) {
                return true;
            }
            if (System.nanoTime() - startTime >= waitNanos) {
                return false;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(10),
                        Math.max(1, waitNanos - (System.nanoTime() - startTime))));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    @Override
    public void unlock() {
        // 调用lua
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());
    }

    /*@Override
    public void unlock() {
        //获得线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 判断是否一致
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
