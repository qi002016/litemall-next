package com.litemall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * D7 专题详情要按商品 id 批量取简要信息，只能经 GoodsClient Feign 调 /inner/**（§3.3），
 * 因此本服务也要像 goods/order/admin 一样开启 Feign 客户端扫描。
 */
@EnableFeignClients
@SpringBootApplication
public class LitemallMarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LitemallMarketingApplication.class, args);
    }

}
