package com.litemall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Ad;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.AdVO;
import com.litemall.domain.dto.AdDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AdMapper;
import com.litemall.service.impl.AdServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R3-02 广告服务契约自检（纯 Mockito，不连 MySQL / Redis，不启动 Spring）。
 * 覆盖 D1 位置白名单、D2b PageVO 形状、D3 时间窗空即不限、D4 校验与落库、
 * D5 排序、D6 afterCommit 缓存失效、BR-CMS-03 limit 截断。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdServiceImplTest {

    @Mock
    private AdMapper adMapper;

    @Mock
    private CacheService cacheService;

    private AdServiceImpl adService;

    @BeforeEach
    void setUp() {
        adService = new AdServiceImpl(cacheService);
        ReflectionTestUtils.setField(adService, "baseMapper", adMapper);
    }

    @AfterEach
    void tearDown() {
        // 手动开启的事务同步是线程本地状态，必须清掉，避免污染后续用例
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ---------------------------------------------------------------- D1 / D2b

    @Test
    @DisplayName("D1/D2：未知 position（含前端漏传 null）→ 空 PageVO，不查库也不读缓存")
    void getAdList_unknownPosition_emptyPage() {
        PageVO<AdVO> page = adService.getAdList("unknown", 5);

        assertEquals(0L, page.getTotal().longValue());
        assertEquals(0L, page.getPages().longValue());
        assertTrue(page.getList().isEmpty());
        verifyNoInteractions(cacheService);
        verify(adMapper, never()).selectList(any());

        PageVO<AdVO> nullPosition = adService.getAdList(null, 5);
        assertTrue(nullPosition.getList().isEmpty());
    }

    @Test
    @DisplayName("D1/D2b：home_banner 返回 PageVO{total,pages,list}，AdVO 带 linkType/sortOrder/position")
    void getAdList_homeBanner_pageShape() {
        stubCacheLoader();
        when(adMapper.selectList(any())).thenReturn(List.of(
                ad(995001L, 1, 10, "993001"),
                ad(995002L, 2, 20, "996001")));

        PageVO<AdVO> page = adService.getAdList("home_banner", 5);

        assertEquals(2L, page.getTotal().longValue());
        assertEquals(1L, page.getPages().longValue());
        assertEquals(2, page.getList().size());
        AdVO first = page.getList().get(0);
        assertEquals(995001L, first.getId().longValue());
        assertEquals("home_banner", first.getPosition());
        assertEquals(1, first.getLinkType());
        assertEquals(10, first.getSortOrder());
        assertEquals("993001", first.getLink());
    }

    // ---------------------------------------------------------------- D3 / D5 / limit

    @Test
    @DisplayName("D1/D3/D5：position 映射成数字 1，时间窗空即不限，排序 sort_order ASC,id DESC")
    void getAdList_sqlConditions() {
        stubCacheLoader();
        when(adMapper.selectList(any())).thenReturn(List.of());

        adService.getAdList("home_banner", 5);

        LambdaQueryWrapper<Ad> wrapper = captureSelectWrapper();
        String sql = wrapper.getSqlSegment();
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        // D1：eq 的是 Integer 1，不再把 "home_banner" 字符串塞给 tinyint 列
        assertTrue(params.contains(1), "position 应映射成数字 1");
        // D3：NULL 的行不被排除，且用 <= / >= 而不是旧的 < / >
        assertTrue(sql.contains("start_time IS NULL"));
        assertTrue(sql.contains("end_time IS NULL"));
        assertTrue(sql.contains("start_time <="));
        assertTrue(sql.contains("end_time >="));
        // D5：排序规则
        assertTrue(sql.contains("ORDER BY sort_order ASC,id DESC"));
    }

    @Test
    @DisplayName("BR-CMS-03：limit 默认 5、最大 5，服务层截断且不同 limit 不污染缓存")
    void getAdList_limitTruncation() {
        stubCacheLoader();
        when(adMapper.selectList(any())).thenReturn(List.of(
                ad(995001L, 1, 10, "1"), ad(995002L, 1, 20, "2"),
                ad(995003L, 1, 30, "3"), ad(995004L, 1, 40, "4"),
                ad(995005L, 1, 50, "5")));

        assertEquals(3, adService.getAdList("home_banner", 3).getList().size());
        assertEquals(5, adService.getAdList("home_banner", 99).getList().size());
        assertEquals(5, adService.getAdList("home_banner", null).getList().size());
        // 三次共用同一个缓存 key，缓存只回源一批最多 5 条
        verify(cacheService, times(3)).getList(eq("ad:list:home_banner"), eq(AdVO.class),
                eq(300L), eq(60L), any());
    }

    // ---------------------------------------------------------------- D4

    @Test
    @DisplayName("D4：create 落库 linkType/sortOrder，link 保留商品/专题 ID 字符串")
    void createAd_persistsNewFields() {
        stubInsert();
        AdDTO dto = new AdDTO();
        dto.setName("跳商品");
        dto.setLinkType(1);
        dto.setLink("993001");
        dto.setSortOrder(10);
        dto.setPosition(1);

        Long id = adService.createAd(dto);

        assertEquals(995001L, id.longValue());
        ArgumentCaptor<Ad> captor = ArgumentCaptor.forClass(Ad.class);
        verify(adMapper).insert(captor.capture());
        Ad saved = captor.getValue();
        assertEquals(1, saved.getLinkType());
        assertEquals(10, saved.getSortOrder());
        assertEquals("993001", saved.getLink());
        assertEquals(1, saved.getPosition());
    }

    @Test
    @DisplayName("D4：linkType=1/2 时 link 非空数字否则 400 PARAM_INVALID，且不落库不失效缓存")
    void createAd_invalidLink_rejected() {
        for (int linkType : List.of(1, 2)) {
            AdDTO dto = new AdDTO();
            dto.setName("脏链接");
            dto.setLinkType(linkType);
            dto.setLink("/pages/coupon/list");
            BadRequestException ex = assertThrows(BadRequestException.class, () -> adService.createAd(dto));
            assertEquals("PARAM_INVALID", ex.getErrorCode());

            AdDTO blank = new AdDTO();
            blank.setName("空链接");
            blank.setLinkType(linkType);
            blank.setLink("   ");
            assertThrows(BadRequestException.class, () -> adService.createAd(blank));
        }
        verify(adMapper, never()).insert(any(Ad.class));
        verify(cacheService, never()).delByPrefix(anyString());
    }

    @Test
    @DisplayName("D4：linkType=0 时 link 置空，不带脏 URL 进库")
    void createAd_linkTypeZero_clearsLink() {
        stubInsert();
        AdDTO dto = new AdDTO();
        dto.setName("无跳转");
        dto.setLinkType(0);
        dto.setLink("/pages/coupon/list");

        adService.createAd(dto);

        ArgumentCaptor<Ad> captor = ArgumentCaptor.forClass(Ad.class);
        verify(adMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getLinkType());
        assertNull(captor.getValue().getLink());
        assertEquals(0, captor.getValue().getSortOrder());
    }

    @Test
    @DisplayName("D4：linkType 非 0/1/2 → 400 PARAM_INVALID")
    void createAd_badLinkType_rejected() {
        AdDTO dto = new AdDTO();
        dto.setName("非法类型");
        dto.setLinkType(3);
        dto.setLink("993001");
        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> adService.createAd(dto)).getErrorCode());
        verify(adMapper, never()).insert(any(Ad.class));
    }

    @Test
    @DisplayName("D4：update 时 linkType=0 显式 SET link=NULL（不被 updateById 的 NOT_NULL 策略跳过）")
    void updateAd_linkTypeZero_clearsLink() {
        when(adMapper.selectById(995001L)).thenReturn(ad(995001L, 1, 10, "/pages/coupon/list"));
        when(adMapper.updateById(any(Ad.class))).thenReturn(1);
        AdDTO dto = new AdDTO();
        dto.setId(995001L);
        dto.setLinkType(0);
        dto.setLink("/pages/coupon/list");

        adService.updateAd(dto);

        ArgumentCaptor<Ad> captor = ArgumentCaptor.forClass(Ad.class);
        verify(adMapper).updateById(captor.capture());
        assertNull(captor.getValue().getLink());
        assertEquals(0, captor.getValue().getLinkType());
        // 额外的显式 SET link=NULL
        verify(adMapper).update(isNull(), any());
    }

    // ---------------------------------------------------------------- D6

    @Test
    @DisplayName("D6：无事务上下文时 create/update/delete 立即 delByPrefix(\"ad:list:\")")
    void writeEntries_noTransaction_delImmediately() {
        stubInsert();
        AdDTO create = new AdDTO();
        create.setName("新建");
        create.setLinkType(1);
        create.setLink("993001");
        adService.createAd(create);

        when(adMapper.selectById(995001L)).thenReturn(ad(995001L, 1, 10, "993001"));
        when(adMapper.updateById(any(Ad.class))).thenReturn(1);
        AdDTO update = new AdDTO();
        update.setId(995001L);
        update.setName("改名");
        adService.updateAd(update);
        adService.deleteAd(995001L);

        verify(cacheService, times(3)).delByPrefix("ad:list:");
    }

    @Test
    @DisplayName("D6：事务激活时注册 afterCommit，提交前不删、提交后只删一次")
    void writeEntries_afterCommit_delayed() {
        stubInsert();
        TransactionSynchronizationManager.initSynchronization();

        AdDTO create = new AdDTO();
        create.setName("新建");
        create.setLinkType(1);
        create.setLink("993001");
        adService.createAd(create);

        verify(cacheService, never()).delByPrefix(anyString());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(cacheService, times(1)).delByPrefix("ad:list:");
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private void stubCacheLoader() {
        when(cacheService.getList(anyString(), eq(AdVO.class), anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    Supplier<List<AdVO>> loader = inv.getArgument(4, Supplier.class);
                    return loader.get();
                });
    }

    private void stubInsert() {
        when(adMapper.insert(any(Ad.class))).thenAnswer(inv -> {
            Ad a = inv.getArgument(0);
            a.setId(995001L);
            return 1;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<Ad> captureSelectWrapper() {
        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(adMapper).selectList(captor.capture());
        return captor.getValue();
    }

    private Ad ad(Long id, Integer linkType, Integer sortOrder, String link) {
        return new Ad()
                .setId(id)
                .setName("轮播" + id)
                .setLink(link)
                .setUrl("https://cdn/a.jpg")
                .setPosition(1)
                .setContent("内容")
                .setEnabled(true)
                .setDeleted(false)
                .setLinkType(linkType)
                .setSortOrder(sortOrder)
                .setAddTime(LocalDateTime.now().minusDays(1))
                .setUpdateTime(LocalDateTime.now());
    }
}
