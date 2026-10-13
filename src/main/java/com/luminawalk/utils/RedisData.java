package com.luminawalk.utils;

import lombok.Data;

import java.time.LocalDateTime;


//普通缓存直接保存地点对象的 JSON；逻辑过期缓存则多包一层：
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
