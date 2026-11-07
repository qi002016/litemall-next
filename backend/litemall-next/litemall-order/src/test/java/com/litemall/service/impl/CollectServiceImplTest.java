package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.CollectAddDTO;
import com.litemall.domain.PO.Collect;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CollectVO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CollectMapper;
import com.litemall.utils.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T1「我的收藏」服务层单测（R3 §6.8）。
 * 沿用 {@link CartServiceImplTest} 的最轻方式：纯 Mockito + 反射补上 ServiceImpl 的
 * baseMapper/entityClass，不起 Spring 容器、不连库；lambda 条件所需 TableInfo 静态初始化一次。
 */
@ExtendWith(MockitoExtension.class)
class CollectServiceImplTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Collect.class);
    }

    private static final Long USER_ID = 7L;
    private static final Long GOODS_ID = 100L;
    private static final Long GOODS_ID_2 = 200L;

    @Mock
    private CollectMapper collectMapper;
    @Mock
    private GoodsClient goodsClient;

    private CollectServiceImpl collectService;

    @BeforeEach
    void setUp() {
        collectService = new CollectServiceImpl(goodsClient);
        ReflectionTestUtils.setField(collectService, "baseMapper", collectMapper);
        ReflectionTestUtils.setField(collectService, "entityClass", Collect.class);
        UserContext.setUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /* ==================== 收藏写入 ==================== */

    @Test
    @DisplayName("重复收藏只 1 条：第二次命中已有记录，不再插入")
    void addCollect_duplicate_onlyOneRow() {
        when(goodsClient.listGoodsBriefByIds(any())).thenReturn(List.of(brief(GOODS_ID, 3900, true)));
        when(collectMapper.selectOne(any())).thenReturn(null, collect(1L, GOODS_ID, LocalDateTime.now()));

        assertThat(collectService.addCollect(dto(0))).isTrue();
        assertThat(collectService.addCollect(dto(0))).isTrue();

        verify(collectMapper, times(1)).insert(any(Collect.class));
    }

    @Test
    @DisplayName("并发双击：insert 撞唯一键同样返回 true，不冒泡 500")
    void addCollect_duplicateKey_stillCollected() {
        when(goodsClient.listGoodsBriefByIds(any())).thenReturn(List.of(brief(GOODS_ID, 3900, true)));
        when(collectMapper.selectOne(any())).thenReturn(null);
        when(collectMapper.insert(any(Collect.class)))
                .thenThrow(new DuplicateKeyException("uk_collect_user_value_type"));

        assertThat(collectService.addCollect(dto(0))).isTrue();
    }

    @Test
    @DisplayName("取消后重新收藏：取消走物理删除，重新收藏能再次插入且不再逻辑删除")
    void addCollect_cancelThenRecollect() {
        when(goodsClient.listGoodsBriefByIds(any())).thenReturn(List.of(brief(GOODS_ID, 3900, true)));
        when(collectMapper.selectOne(any())).thenReturn(null);

        assertThat(collectService.addCollect(dto(1))).isFalse();
        assertThat(collectService.addCollect(dto(0))).isTrue();

        verify(collectMapper, times(1)).delete(any());
        verify(collectMapper, never()).updateById(any(Collect.class));
        verify(collectMapper, times(1)).insert(any(Collect.class));
    }

    @Test
    @DisplayName("商品不存在或已删除 → 400 PARAM_INVALID，且不写库")
    void addCollect_goodsMissing_paramInvalid() {
        when(goodsClient.listGoodsBriefByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> collectService.addCollect(dto(0)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo(BadRequestException.PARAM_INVALID);

        verify(collectMapper, never()).insert(any(Collect.class));
    }

    /* ==================== 收藏列表 ==================== */

    @Test
    @DisplayName("下架商品 onSale=false；商品查不到时名称/图/价格留 null 且 onSale=false")
    void listCollect_offSale_andMissingGoods() {
        LocalDateTime t2 = LocalDateTime.of(2026, 11, 7, 10, 0);
        LocalDateTime t1 = t2.minusDays(1);
        // 只给 GOODS_ID 返回 brief，GOODS_ID_2 模拟被物理删、查不到
        when(goodsClient.listGoodsBriefByIds(any()))
                .thenReturn(List.of(brief(GOODS_ID, 12900, false)));
        when(collectMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Collect> page = inv.getArgument(0);
            page.setRecords(List.of(collect(1L, GOODS_ID, t2), collect(2L, GOODS_ID_2, t1)));
            page.setTotal(2);
            return page;
        });

        PageVO<CollectVO> page = collectService.listCollect(1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getList()).hasSize(2);
        CollectVO offSale = page.getList().get(0);
        assertThat(offSale.getGoodsName()).isEqualTo("商品100");
        assertThat(offSale.getPrice()).isEqualTo(12900);
        assertThat(offSale.getOnSale()).isFalse();

        CollectVO missing = page.getList().get(1);
        assertThat(missing.getGoodsName()).isNull();
        assertThat(missing.getPicUrl()).isNull();
        assertThat(missing.getPrice()).isNull();
        assertThat(missing.getOnSale()).isFalse();
    }

    @Test
    @DisplayName("分页倒序：按收藏时间倒序装配，价格字段来自扩展后的 brief")
    void listCollect_orderByAddTimeDesc() {
        LocalDateTime t1 = LocalDateTime.of(2026, 11, 5, 9, 0);
        LocalDateTime t2 = t1.plusDays(1);
        LocalDateTime t3 = t2.plusDays(1);
        when(goodsClient.listGoodsBriefByIds(any())).thenReturn(List.of(
                brief(1L, 3900, true), brief(2L, 5900, true), brief(3L, 7900, true)));
        when(collectMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<Collect> page = inv.getArgument(0);
            // 模拟 DB 侧 orderByDesc(add_time) 返回的结果
            page.setRecords(List.of(collect(3L, 3L, t3), collect(2L, 2L, t2), collect(1L, 1L, t1)));
            page.setTotal(3);
            return page;
        });

        PageVO<CollectVO> page = collectService.listCollect(1, null);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getList()).extracting(CollectVO::getId).containsExactly(3L, 2L, 1L);
        assertThat(page.getList()).extracting(CollectVO::getAddTime)
                .containsExactly("2026-11-07 09:00:00", "2026-11-06 09:00:00", "2026-11-05 09:00:00");
        assertThat(page.getList().get(0).getPrice()).isEqualTo(7900);
    }

    /* ==================== 夹具 ==================== */

    private CollectAddDTO dto(int type) {
        CollectAddDTO dto = new CollectAddDTO();
        dto.setGoodsId(GOODS_ID);
        dto.setType(type);
        return dto;
    }

    private GoodsBriefDTO brief(Long goodsId, Integer price, boolean onSale) {
        GoodsBriefDTO brief = new GoodsBriefDTO();
        brief.setId(goodsId);
        brief.setName("商品" + goodsId);
        brief.setPicUrl("https://example.com/" + goodsId + ".png");
        brief.setPrice(price);
        brief.setOnSale(onSale);
        return brief;
    }

    private Collect collect(Long id, Long goodsId, LocalDateTime addTime) {
        return new Collect()
                .setId(id)
                .setUserId(USER_ID)
                .setValueId(goodsId)
                .setType(0)
                .setAddTime(addTime)
                .setUpdateTime(addTime)
                .setDeleted(false);
    }
}
