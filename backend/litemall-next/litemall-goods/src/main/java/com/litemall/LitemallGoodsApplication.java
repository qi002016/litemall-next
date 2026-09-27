package com.litemall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class LitemallGoodsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LitemallGoodsApplication.class, args);
    }

}
