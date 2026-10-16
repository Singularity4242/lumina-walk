package com.luminawalk.utils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SlidingWindowLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowLimitAspect.class);
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();
    private static final String LIMIT_SCRIPT =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])\n"
                    + "local count = redis.call('ZCOUNT', KEYS[1], ARGV[2], ARGV[1])\n"
                    + "if count >= tonumber(ARGV[4]) then return 1 end\n"
                    + "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])\n"
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[5])\n"
                    + "return 0";

    @Resource
    private RedissonClient redissonClient;

    @Around("@annotation(com.luminawalk.utils.SlidingWindowLimit)"
            + " || @within(com.luminawalk.utils.SlidingWindowLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        Method method = AopUtils.getMostSpecificMethod(
                ((MethodSignature) joinPoint.getSignature()).getMethod(), targetClass);
        SlidingWindowLimit classLimit = AnnotatedElementUtils.findMergedAnnotation(
                targetClass, SlidingWindowLimit.class);
        SlidingWindowLimit methodLimit = AnnotatedElementUtils.findMergedAnnotation(
                method, SlidingWindowLimit.class);
        // 类级全局限流与方法级用户限流叠加生效。
        if (classLimit != null) {
            checkLimit(classLimit, method, joinPoint, attributes);
        }
        if (methodLimit != null) {
            checkLimit(methodLimit, method, joinPoint, attributes);
        }
        return joinPoint.proceed();
    }

    private void checkLimit(SlidingWindowLimit limit, Method method,
                            ProceedingJoinPoint joinPoint, ServletRequestAttributes attributes) {
        long windowMillis = limit.timeUnit().toMillis(limit.windowSize());
        if (windowMillis <= 0 || limit.maxRequests() <= 0) {
            throw new IllegalArgumentException("限流窗口和请求数必须为正，窗口至少为 1 毫秒");
        }
        String key;
        if (limit.globalLimit()) {
            // 同一策略的全局额度由所有被标注的接口、用户共同使用，不按 IP 拆分。
            key = RATE_LIMIT_PREFIX + "global:" + windowMillis + ":" + limit.maxRequests();
        } else {
            if (UserHolder.getUser() == null || UserHolder.getUser().getId() == null) {
                throw new RateLimitExceededException("请先登录");
            }
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), PARAMETER_NAMES);
            context.setVariable("request", attributes.getRequest());
            Object keyValue = PARSER.parseExpression(limit.keySpEL()).getValue(context);
            if (keyValue == null) {
                throw new IllegalArgumentException("限流参数不能为空");
            }
            key = RATE_LIMIT_PREFIX + "user:" + UserHolder.getUser().getId() + ":"
                    + method.toGenericString() + ":" + keyValue;
        }
        if (isRateLimited(key, windowMillis, limit.maxRequests())) {
            throw new RateLimitExceededException("请求过于频繁，请稍后重试");
        }
    }

    private boolean isRateLimited(String key, long windowMillis, int maxRequests) {
        long now = System.currentTimeMillis();
        try {
            // Lua 参数需要明文数字，不能使用 Redisson 默认的对象序列化编码。
            Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                    RScript.Mode.READ_WRITE, LIMIT_SCRIPT, RScript.ReturnType.INTEGER,
                    Collections.singletonList(key), now, now - windowMillis,
                    UUID.randomUUID().toString(), maxRequests, windowMillis * 2);
            return Long.valueOf(1).equals(result);
        } catch (Exception e) {
            // 保留上游的故障策略：Redis 限流检查失败时放行。
            log.error("限流服务异常，降级放行", e);
            return false;
        }
    }
}
