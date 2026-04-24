package com.example.ShoppingSystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.ShoppingSystem.mapper")
public class ShoppingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingSystemApplication.class, args);
    }
}
