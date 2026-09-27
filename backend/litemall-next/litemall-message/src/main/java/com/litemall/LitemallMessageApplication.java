package com.litemall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class LitemallMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(LitemallMessageApplication.class, args);
    }

}
