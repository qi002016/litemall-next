package com.litemall.controller;

import com.jayway.jsonpath.JsonPath;
import com.litemall.domain.PO.Brand;
import com.litemall.mapper.BrandMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-10 缺陷回归：C 端品牌列表 {@code GET /app/goods/brand/list}（CI-34）。
 *
 * <p>
 * 锁死两条验收缺陷：
 * </p>
 * <ul>
 *   <li><b>D-A</b>：不传 {@code pageNum}/{@code pageSize} 时按契约默认值（{@code 1}/{@code 20}）返回 200，
 *       绝不允许原先裸 {@code int} 参数造成的 500（{@code {"code":500,"msg":"系统内部异常"}}）；</li>
 *   <li><b>D-B</b>：{@code floorPrice} 出参为<b>整数分</b>（库内 {@code 39.00} 元 ⇒ {@code 3900}），
 *       而不是元小数字符串/浮点。</li>
 * </ul>
 * <p>
 * 夹具品牌名带本次 JVM 唯一后缀，插 21 条以把默认 {@code pageSize=20} 的分页行为暴露出来
 * （总数 &gt; 20 时才能区分 20 与 100）；{@code finally} 里物理删除（品牌无逻辑删除，见 CN-28 口径）。
 * </p>
 */
@SpringBootTest
class BrandListApiTest {

    /** 每次 JVM 执行唯一的名称后缀：唯一键 uk_brand_name 含 name，反复跑不能互相干扰 */
    private static final String SUFFIX = Long.toString(System.currentTimeMillis() % 1_000_000);

    /** 契约默认每页条数（docs/design/06 分页口径） */
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BrandMapper brandMapper;

    private MockMvc mockMvc;

    private final List<Long> createdIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @AfterEach
    void tearDown() {
        createdIds.forEach(id -> brandMapper.deleteById(id));
        createdIds.clear();
    }

    @Test
    @DisplayName("D-A：不传分页参数 ⇒ 200 + pageNum=1/pageSize=20（原先 500）")
    void missingPaginationParamsFallBackToDefaults() throws Exception {
        insertBrands(21);

        String body = mockMvc.perform(get("/app/goods/brand/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        long total = longOf(JsonPath.read(body, "$.data.total"));
        long pages = longOf(JsonPath.read(body, "$.data.pages"));
        List<Map<String, Object>> rows = rowsOf(body);

        assertThat(total).as("夹具已插 21 条，总数必须覆盖默认页大小").isGreaterThanOrEqualTo(21L);
        assertThat(rows).as("默认 pageSize 必须为 20").hasSize(DEFAULT_PAGE_SIZE);
        assertThat(pages).as("pages 按 pageSize=20 计算").isEqualTo((total + 19) / 20);
    }

    @Test
    @DisplayName("D-A：pageSize=0 / 负数 / 空串 ⇒ 落默认 20，不得 500")
    void illegalPaginationValuesFallBackToDefaults() throws Exception {
        insertBrands(21);

        String body = mockMvc.perform(get("/app/goods/brand/list")
                        .param("pageNum", "0")
                        .param("pageSize", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(rowsOf(body)).hasSize(DEFAULT_PAGE_SIZE);

        String blank = mockMvc.perform(get("/app/goods/brand/list")
                        .param("pageNum", "")
                        .param("pageSize", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(rowsOf(blank)).hasSize(DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("D-A：显式分页参数生效（pageNum=1&pageSize=5）")
    void explicitPaginationWorks() throws Exception {
        insertBrands(21);

        String body = mockMvc.perform(get("/app/goods/brand/list")
                        .param("pageNum", "1")
                        .param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        long total = longOf(JsonPath.read(body, "$.data.total"));
        assertThat(rowsOf(body)).hasSize(5);
        assertThat(longOf(JsonPath.read(body, "$.data.pages"))).isEqualTo((total + 4) / 5);
    }

    @Test
    @DisplayName("D-A：非数字 pageNum ⇒ HTTP 200 + code 400 + PARAM_INVALID（不落 500）")
    void nonNumericPageNumIsParamInvalid() throws Exception {
        mockMvc.perform(get("/app/goods/brand/list").param("pageNum", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("PARAM_INVALID"))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("pageNum")));
    }

    @Test
    @DisplayName("D-B：floorPrice 为整数分（库内 39.00 元 ⇒ 3900）")
    void floorPriceIsIntegerCents() throws Exception {
        String name = "价格品牌" + SUFFIX;
        insertBrand(name, new BigDecimal("39.00"));

        String body = mockMvc.perform(get("/app/goods/brand/list").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<String, Object> row = rowsOf(body).stream()
                .filter(item -> name.equals(item.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("新建品牌必须出现在列表里：" + name));
        // Long 走全局 ToStringSerializer ⇒ JSON 里是字符串 "3900"
        assertThat(String.valueOf(row.get("floorPrice")))
                .as("39.00 元必须出参 3900 分，而不是 39.0 / 39")
                .isEqualTo("3900");
    }

    /* ==================== 夹具 ==================== */

    private void insertBrands(int count) {
        for (int i = 0; i < count; i++) {
            insertBrand("批量品牌" + SUFFIX + "_" + i, new BigDecimal("1.00"));
        }
    }

    private void insertBrand(String name, BigDecimal floorPrice) {
        LocalDateTime now = LocalDateTime.now();
        Brand brand = new Brand()
                .setName(name)
                .setDesc("")
                .setPicUrl("")
                .setSortOrder(0)
                .setFloorPrice(floorPrice)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        brandMapper.insert(brand);
        createdIds.add(brand.getId());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rowsOf(String body) {
        return JsonPath.read(body, "$.data.list");
    }

    private long longOf(Object value) {
        return Long.parseLong(String.valueOf(value));
    }
}