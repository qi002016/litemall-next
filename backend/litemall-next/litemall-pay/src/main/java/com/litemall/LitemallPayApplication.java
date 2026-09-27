package com.litemall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class LitemallPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LitemallPayApplication.class, args);
    }

}
