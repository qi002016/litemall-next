package com.litemall.controller;

import com.litemall.domain.DTO.AdminCreateDTO;
import com.litemall.domain.DTO.AdminResetPwdDTO;
import com.litemall.domain.DTO.AftersaleRejectReqDTO;
import com.litemall.domain.DTO.GoodsCreateDTO;
import com.litemall.domain.DTO.GoodsUpdateDTO;
import com.litemall.domain.DTO.OrderCloseReqDTO;
import com.litemall.domain.DTO.OrderShipReqDTO;
import com.litemall.domain.DTO.OrderUpdateShipReqDTO;
import com.litemall.domain.DTO.StockUpdateReqDTO;
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
 *       {@code MethodArgumentNotValidException} 分支确实生效（对外 {@code /admin} 恒 HTTP 200，
 *       业务码在 body：{@code code=400} + {@code data.errorCode=PARAM_INVALID}）；</li>
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
    @DisplayName("M13：登录账号为空 → HTTP 200 + code=400 + errorCode=PARAM_INVALID（R1 契约）")
    void login_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("PARAM_INVALID"))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("账号不能为空")));
    }

    @Test
    @DisplayName("M13：登录密码为空 → HTTP 200 + code=400 + errorCode=PARAM_INVALID（R1 契约）")
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"root\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("PARAM_INVALID"))
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
    @DisplayName("R1：新建商品——名称为空被拒")
    void goodsCreate_blankName_invalid() {
        GoodsCreateDTO dto = new GoodsCreateDTO();
        dto.setName("");
        dto.setMinPrice(990);

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("商品名称不能为空"));
    }

    @Test
    @DisplayName("R1：新建商品——最低售价 <1 分被拒（08 v4：minPrice ≥1 分，单位分）")
    void goodsCreate_priceBelowOneCent_invalid() {
        GoodsCreateDTO dto = new GoodsCreateDTO();
        dto.setName("示例商品");
        dto.setMinPrice(-1);

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("最低售价必须 ≥1 分"));
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
    @DisplayName("R1：改物流单号——快递公司/单号为空被拒（CN-12 两者必传）")
    void orderUpdateShip_blankShipInfo_invalid() {
        OrderUpdateShipReqDTO dto = new OrderUpdateShipReqDTO();
        dto.setOrderId(1L);
        dto.setShipChannel("  ");
        dto.setShipSn("");

        assertThat(messages(dto))
                .anySatisfy(m -> assertThat(m).contains("快递公司不能为空"))
                .anySatisfy(m -> assertThat(m).contains("快递单号不能为空"));
    }

    @Test
    @DisplayName("R1：关单——订单ID为空被拒，原因为空合法（CN-10 未定义必填）")
    void orderClose_nullOrderId_invalid() {
        OrderCloseReqDTO bad = new OrderCloseReqDTO();
        assertThat(messages(bad)).anySatisfy(m -> assertThat(m).contains("订单ID不能为空"));

        OrderCloseReqDTO ok = new OrderCloseReqDTO();
        ok.setOrderId(1L);
        assertThat(validator.validate(ok)).isEmpty();
    }

    @Test
    @DisplayName("R1：售后驳回——原因为空被拒（CN-04 必填）")
    void aftersaleReject_blankReason_invalid() {
        AftersaleRejectReqDTO dto = new AftersaleRejectReqDTO();
        dto.setAftersaleId(3L);
        dto.setReason("   ");

        assertThat(messages(dto)).anySatisfy(m -> assertThat(m).contains("驳回原因不能为空"));
    }

    @Test
    @DisplayName("R1：库存调整——货品ID/库存数量为空、数量为负都被拒（CN-14 绝对值为准）")
    void stockUpdate_invalid() {
        StockUpdateReqDTO bad = new StockUpdateReqDTO();
        bad.setNumber(-1);

        assertThat(messages(bad))
                .anySatisfy(m -> assertThat(m).contains("货品ID不能为空"))
                .anySatisfy(m -> assertThat(m).contains("库存数量不能为负"));

        StockUpdateReqDTO ok = new StockUpdateReqDTO();
        ok.setProductId(5L);
        ok.setNumber(0);
        assertThat(validator.validate(ok)).isEmpty();
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