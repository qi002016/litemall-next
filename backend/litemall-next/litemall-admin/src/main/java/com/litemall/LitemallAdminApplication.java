package com.litemall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * <p>
 * 运营后台服务。
 * </p>
 * <p>
 * H5：admin 不再直写 order/goods/marketing/user 域的表，改为经 {@code litemall-api}
 * 的 Feign 客户端调用各服务 {@code /inner/**} 接口，因此需要开启 Feign 客户端扫描。
 * </p>
 */
@EnableFeignClients
@SpringBootApplication
public class LitemallAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(LitemallAdminApplication.class, args);
    }
}