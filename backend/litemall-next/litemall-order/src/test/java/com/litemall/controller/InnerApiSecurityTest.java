package com.litemall.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H3：验证 {@code /inner/**} 拦截器真正挂载生效（接线级验证，非纯单元测试）。
 * <p>
 * 通过 {@code hm.internal.token} 注入密钥，使 dev 环境的「空密钥放行」分支不生效，
 * 从而断言无密钥/错误密钥的调用被拒。请求被拦截器挡在控制器之前，不会触达数据库。
 * <p>
 * 注：Boot 4 中 {@code @AutoConfigureMockMvc} 迁至 {@code spring-boot-webmvc-test}，
 * 该依赖不在本模块 classpath；这里用 spring-test 的 {@code MockMvcBuilders} 手工装配，
 * 既验证真实拦截器/异常处理器接线，又不引入新依赖。
 */
@SpringBootTest(properties = "hm.internal.token=test-inner-secret")
class InnerApiSecurityTest {

    private static final String BODY = "{\"orderId\":1,\"payId\":\"p1\"}";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("H3：缺少 X-Internal-Token 的内部调用必须 401")
    void innerApiWithoutToken_unauthorized() throws Exception {
        mockMvc.perform(post("/inner/order/markPaid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("H3：X-Internal-Token 错误的内部调用必须 401")
    void innerApiWithWrongToken_unauthorized() throws Exception {
        mockMvc.perform(post("/inner/order/markPaid")
                        .header("X-Internal-Token", "guess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}