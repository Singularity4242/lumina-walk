package com.luminawalk.utils;

import com.luminawalk.config.RedissonConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用真实 Redis 验证限流核心，不启动整个 Spring 应用，也不依赖 MySQL、RabbitMQ。
 *
 * 执行：mvn -Dredis.integration=true -Dtest=SlidingWindowLimitRedisIntegrationTest test
 * 不指定 redis.integration=true 时跳过，普通 mvn test 不会因此必须连接 Redis。
 *
 * 调用的是业务类中实际使用的 Lua，没有复制脚本，也没有模拟 Redis 返回值。
 * 此处验证“限流方法 -> Redis”；Controller 注解、用户身份和 HTTP 429 由切面测试覆盖。
 */
@EnabledIfSystemProperty(named = "redis.integration", matches = "true")
class SlidingWindowLimitRedisIntegrationTest {

    private static RedissonClient redisson;

    private SlidingWindowLimitAspect aspect;
    private final List<String> testKeys = new ArrayList<>();

    @BeforeAll
    static void connectRedis() {
        // 直接复用应用的连接配置。手动创建客户端，不会加载整个 Spring 上下文。
        // Redis 未启动或认证失败时测试应失败，不能把连接失败当作验证通过。
        redisson = new RedissonConfig().redissonClient();
    }

    @BeforeEach
    void setUp() {
        aspect = new SlidingWindowLimitAspect();
        // 手动注入真实客户端，相当于替代 Spring 的 @Resource 注入。
        ReflectionTestUtils.setField(aspect, "redissonClient", redisson);
    }

    @AfterEach
    void deleteTestKeys() {
        // 只删除本次测试创建的 Key，不清空数据库，不操作业务限流或库存数据。
        for (String key : testKeys) {
            redisson.getKeys().delete(key);
        }
    }

    @AfterAll
    static void disconnectRedis() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    @DisplayName("同一窗口内前三次放行，第四次拒绝，拒绝的请求不写入 ZSet")
    void rejectsRequestsAboveLimit() {
        String key = newTestKey();

        // 用较长窗口避免测试执行时间跨过窗口；返回 true 表示被限流。
        for (int i = 0; i < 3; i++) {
            assertFalse(isRateLimited(key, 30_000, 3), "额度内的请求应放行");
        }
        assertTrue(isRateLimited(key, 30_000, 3), "第四次请求应被拒绝");
        assertEquals(3, requests(key).size(), "拒绝的请求不应占用新的 ZSet 记录");
    }

    @Test
    @DisplayName("短窗口的 Key 按上游规则至少保留 60 秒")
    void keepsShortWindowKeyForSixtySeconds() {
        String key = newTestKey();
        long windowMillis = 2_000;

        assertFalse(isRateLimited(key, windowMillis, 3));

        // Lua 用 EXPIRE 设置秒数，但 remainTimeToLive() 仍以毫秒返回剩余时间。
        // 留出 10 秒执行余量，同时排除旧实现的 4 秒 TTL 及秒/毫秒混用。
        long ttl = requests(key).remainTimeToLive();
        assertTrue(ttl > 50_000 && ttl <= 60_000,
                "TTL 应接近 60000 毫秒，实际为 " + ttl);
    }

    @Test
    @DisplayName("长窗口的 Key 保留窗口的两倍，而不是固定 60 秒")
    void keepsLongWindowKeyForTwiceTheWindow() {
        String key = newTestKey();
        assertFalse(isRateLimited(key, 45_000, 3));
        long ttl = requests(key).remainTimeToLive();
        assertTrue(ttl > 80_000 && ttl <= 90_000,
                "45 秒窗口的 TTL 应接近 90000 毫秒，实际为 " + ttl);
    }

    @Test
    @DisplayName("非正阈值按上游规则放行，仍然记录请求")
    void allowsRequestsWithNonPositiveThreshold() {
        for (int maxRequests : new int[]{0, -1}) {
            String key = newTestKey();
            for (int i = 0; i < 3; i++) {
                assertFalse(isRateLimited(key, 30_000, maxRequests));
            }
            assertEquals(3, requests(key).size(), "应由 Lua 记录请求，不是异常后降级放行");
        }
    }

    @Test
    @DisplayName("Lua 收到非数字阈值时回退为 0，并正常返回放行结果")
    void fallsBackToZeroWhenThresholdIsNotNumeric() {
        String key = newTestKey();
        String script = (String) ReflectionTestUtils.getField(SlidingWindowLimitAspect.class, "LIMIT_SCRIPT");

        // Java 方法的阈值类型是 int，无法传入非法文本，因此直接执行同一份业务脚本。
        // 不经过 Java 的 catch：脚本若报错，这个测试会直接失败，不能用异常放行冒充兜底成功。
        for (int i = 0; i < 3; i++) {
            long now = System.currentTimeMillis();
            Long result = redisson.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER,
                    Collections.singletonList(key), now, now - 30_000,
                    UUID.randomUUID().toString(), "invalid", 60);
            assertEquals(Long.valueOf(0), result);
        }
        assertEquals(3, requests(key).size());
    }

    @Test
    @DisplayName("时间戳较早的请求晚执行时，也不应漏算已放行的较晚请求")
    void countsRequestsThatArriveOutOfTimestampOrder() {
        String key = newTestKey();
        String script = (String) ReflectionTestUtils.getField(SlidingWindowLimitAspect.class, "LIMIT_SCRIPT");
        long earlierTime = System.currentTimeMillis();
        long laterTime = earlierTime + 1;

        // 模拟两个线程：早取到时间戳的线程暂停，晚取时间戳的线程先调用 Redis。
        // 固定传入时间，无需依赖线程调度，就能复现并发请求到达顺序与时间戳不一致。
        Long first = redisson.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER,
                Collections.singletonList(key), laterTime, laterTime - 30_000,
                "later-request", 1, 60);
        Long second = redisson.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, script, RScript.ReturnType.INTEGER,
                Collections.singletonList(key), earlierTime, earlierTime - 30_000,
                "earlier-request", 1, 60);

        assertEquals(Long.valueOf(0), first);
        assertEquals(Long.valueOf(1), second, "已有请求占满额度，晚到达的请求应被拒绝");
        assertEquals(1, requests(key).size(), "被拒绝的乱序请求不应写入 Redis");
    }

    @Test
    @DisplayName("窗口结束后恢复放行，并清理旧记录，而不是依赖整个 Key 过期")
    void allowsRequestAfterWindowSlides() throws InterruptedException {
        String key = newTestKey();

        assertFalse(isRateLimited(key, 2_000, 1));
        assertTrue(isRateLimited(key, 2_000, 1));
        String oldRequestId = requests(key).first();

        // 窗口是 2 秒，Key 的 TTL 至少为 60 秒。等待窗口结束但不等 Key 过期，
        // 这样能验证 Lua 确实删除了窗口外的记录，而不是 Key 恰好自动消失。
        Thread.sleep(2_100);
        assertTrue(requests(key).isExists(), "检查前 Key 应仍存在；机器长时间暂停会影响此时间测试");

        assertFalse(isRateLimited(key, 2_000, 1), "旧请求离开窗口后应恢复放行");
        assertEquals(1, requests(key).size(), "旧记录应被清理，只留下本次请求");
        assertFalse(requests(key).contains(oldRequestId), "原来的请求记录应已删除");
    }

    @Test
    @DisplayName("一个 Key 用完额度，不影响另一个 Key")
    void keepsDifferentKeysIndependent() {
        String firstKey = newTestKey();
        String secondKey = newTestKey();

        assertFalse(isRateLimited(firstKey, 30_000, 1));
        assertTrue(isRateLimited(firstKey, 30_000, 1));
        assertFalse(isRateLimited(secondKey, 30_000, 1));
        assertEquals(1, requests(firstKey).size());
        assertEquals(1, requests(secondKey).size());
    }

    @Test
    @DisplayName("20 个并发请求竞争 3 个名额，只有 3 个请求放行")
    void doesNotExceedLimitUnderConcurrency() throws Exception {
        String key = newTestKey();
        int requestCount = 20;
        long windowMillis = 30_000;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return isRateLimited(key, windowMillis, 3);
                }));
            }
            // 等全部线程准备好，再一起放行，制造竞争；不是在主线程顺序调用 20 次。
            assertTrue(ready.await(5, TimeUnit.SECONDS), "工作线程应及时准备好");
            long startedAt = System.nanoTime();
            start.countDown();

            int allowedCount = 0;
            for (Future<Boolean> result : results) {
                if (!result.get(5, TimeUnit.SECONDS)) {
                    allowedCount++;
                }
            }
            // 所有请求必须处在同一窗口内，否则不能用“只有 3 次放行”判断并发正确性。
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            assertTrue(elapsedMillis < windowMillis, "并发测试不应跨过窗口");
            assertEquals(3, allowedCount, "应恰好放行 3 次，其余 17 次被拒绝");
            assertEquals(3, requests(key).size(), "Redis 中也应恰好有 3 条请求记录");
        } finally {
            // 即使断言失败也停止线程，避免后台任务继续写入测试 Key。
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "测试线程应全部结束");
        }
    }

    private String newTestKey() {
        String key = "test:rate_limit:" + UUID.randomUUID();
        testKeys.add(key);
        return key;
    }

    private RScoredSortedSet<String> requests(String key) {
        // 与业务脚本使用相同的字符串编码，直接查看脚本写入的真实 ZSet。
        return redisson.getScoredSortedSet(key, StringCodec.INSTANCE);
    }

    private boolean isRateLimited(String key, long windowMillis, int maxRequests) {
        // 通过反射调用已有私有方法，无需为测试修改业务方法的可见性。
        // 方法内部仍然执行真实的 StringCodec 编码、Lua 和 Redis 命令。
        return ReflectionTestUtils.invokeMethod(aspect, "isRateLimited", key, windowMillis, maxRequests);
    }
}
