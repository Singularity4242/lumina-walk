package com.luminawalk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.luminawalk.mapper")
@SpringBootApplication
public class LuminaWalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaWalkApplication.class, args);
    }

}
