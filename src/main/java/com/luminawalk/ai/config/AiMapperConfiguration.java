package com.luminawalk.ai.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.luminawalk.ai.mapper")
public class AiMapperConfiguration {
}
