package com.evernox;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EverNox 永夜照相馆 - 主启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.evernox.repository")
public class EvernoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvernoxApplication.class, args);
    }
}
