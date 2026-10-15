package com.luminawalk.utils;

public class RedisConstants {
    /**
     * 登录验证码的 Redis key 前缀，后面拼接手机号。
     */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /**
     * 登录验证码有效期，单位：分钟。
     */
    public static final Long LOGIN_CODE_TTL = 2L;

    /**
     * 登录用户信息的 Redis key 前缀，后面拼接 token。
     */
    public static final String LOGIN_USER_KEY = "login:token:";

    /**
     * 登录状态有效期
     */
    public static final Long LOGIN_USER_TTL = 36000L;

    /**
     * 地理位置数据的 Redis key 前缀，保留现有定义。
     */
    // public static final String SHOP_GEO_KEY = "SHOP:GEO:TYPE:";
    /**
     * 空值缓存有效期，单位：分钟。
     */
    public static final Long CACHE_NULL_TTL = 2L;

    /**
     * 商店信息缓存有效期，单位：分钟。
     */
    public static final Long CACHE_SHOP_TTL = 30L;

    /**
     * 商店key
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    //当前是否有请求在重建该地点缓存
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // 秒杀库存
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
}
