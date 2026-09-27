package com.litemall.controller;

import com.litemall.domain.DTO.AdminCreateDTO;
import com.litemall.domain.DTO.AdminResetPwdDTO;
import com.litemall.domain.DTO.GoodsCreateDTO;
import com.litemall.domain.DTO.GoodsUpdateDTO;
import com.litemall.domain.DTO.OrderRefundReqDTO;
import com.litemall.domain.DTO.OrderShipReqDTO;
import com.litemall.domain.DTO.UserStatusReqDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13：admin 控制器由 {@code Map} 改为 DTO + {@code @Valid} 后的校验行为。
 * <p>
 * 两部分证据：
 * <ol>
 *   <li>HTTP 层：{@code /admin/auth/login} 是 filter 白名单路径，可直接打，
 *       用来证明 {@code @Valid} 与 {@code CommonExceptionAdvice} 的
 *       {@code MethodArgumentNotValidException} 分支确实生效（HTTP 400 + code 400）；</li>
 *   <li>约束层：其余接口需登录，故用 {@code Validator} 直接校验各 DTO 的注解是否覆盖到位。</li>
 * </ol>
 * 注：Boot 4 中 {@code @AutoConfigureMockMvc} 迁至 {@code spring-boot-webmvc-test}（不在本模块 classpath），
 * 故沿用本仓库既有做法——用 spring-test 的 {@code MockMvcBuilders} 手工装配。
 * <p>
 * {@code seata.enabled=false} 是刻意的：本类只验证入参校验，不需要分布式事务，
 * 而 Seata 的 {@code globalTransactionScanner} 会去 {@code 192.168.96.129:8848} 拉注册列表，
 * 拉不到就 {@code Failed to get available servers} 导致整个上下文起不来——
 * 单测不该依赖外部中间件（CI 同理）。这也顺带补上了 admin 模块此前**完全没有**上下文测试的空白。
 */
@SpringBootTest(properties = "seata.enabled=false")
class AdminDtoValidationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /* ==================== HTTP 层 ==================== */

    @Test
    @DisplayName("M13：登录账号为空 → HTTP 400 且 code=400（不再是 200+错误码）")
    void login_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("账号不能为空")));
    }

    @Test
    @DisplayName("M13：登录密码为空 → HTTP 400")
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"root\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("密码不能为空")));
    }

    /* ==================== 约束层 ==================== */

    @Test
    @DisplayName("M13：新建账号——账号为空、密码过短都被拒")
    void adminCreate_blankUsernameAndShortPassword_invalid() {
        AdminCreateDTO dto = new AdminCreateDTO();
        dto.setUsername("  ");
        dto.setPassword("123");

        assertThat(messages(dto))
                .anySatisfy(m -> assertThat(m).contains("账号不能为空"))
                .anySatisfy(m -> assertThat(m).contains("密码长度必须为 6~64 位"));
    }

    @Test
    @DisplayName("M13：新建账号——合法输入无违规")
    void adminCreate_valid() {
        AdminCreateDTO dto = new AdminCreateDTO();
        dto.setUsername("operator");
        dto.setPassword("secret123");

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("M13：重置口令——id 为空被拒（原实现会 NPE）")
    void adminResetPwd_nullId_invalid() {
        AdminResetPwdDTO dto = new AdminResetPwdDTO();
        dto.setPassword("secret123");

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("账号ID不能为空"));
    }

    @Test
    @DisplayName("M13：新建商品——名称为空被拒")
    void goodsCreate_blankName_invalid() {
        GoodsCreateDTO dto = new GoodsCreateDTO();
        dto.setName("");
        dto.setMinPrice(new BigDecimal("9.9"));

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("商品名称不能为空"));
    }

    @Test
    @DisplayName("M13：新建商品——零售价为负被拒")
    void goodsCreate_negativePrice_invalid() {
        GoodsCreateDTO dto = new GoodsCreateDTO();
        dto.setName("示例商品");
        dto.setMinPrice(new BigDecimal("-1"));

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("零售价不能为负"));
    }

    @Test
    @DisplayName("M13：修改商品——id 为空被拒（新建与修改拆成两个 DTO 的原因）")
    void goodsUpdate_nullId_invalid() {
        GoodsUpdateDTO dto = new GoodsUpdateDTO();
        dto.setName("示例商品");

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("商品ID不能为空"));
    }

    @Test
    @DisplayName("M13：发货——订单ID为空被拒")
    void orderShip_nullOrderId_invalid() {
        OrderShipReqDTO dto = new OrderShipReqDTO();
        dto.setShipChannel("SF");

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("订单ID不能为空"));
    }

    @Test
    @DisplayName("M13：退款——金额为负被拒，但留空仍合法（语义是默认实付金额）")
    void orderRefund_negativeAmount_invalidButNullOk() {
        OrderRefundReqDTO negative = new OrderRefundReqDTO();
        negative.setOrderId(1L);
        negative.setRefundAmount(new BigDecimal("-0.01"));
        assertThat(messages(negative)).anySatisfy(m -> assertThat(m).contains("退款金额不能为负"));

        OrderRefundReqDTO noAmount = new OrderRefundReqDTO();
        noAmount.setOrderId(1L);
        assertThat(validator.validate(noAmount)).isEmpty();
    }

    @Test
    @DisplayName("M13：用户状态——越界取值被拒，0/1/2 合法")
    void userStatus_outOfRange_invalid() {
        UserStatusReqDTO bad = new UserStatusReqDTO();
        bad.setId(1L);
        bad.setStatus(9);
        assertThat(messages(bad)).anySatisfy(m -> assertThat(m).contains("状态取值只能是 0/1/2"));

        UserStatusReqDTO good = new UserStatusReqDTO();
        good.setId(1L);
        good.setStatus(2);
        assertThat(validator.validate(good)).isEmpty();
    }

    private Set<String> messages(Object bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}