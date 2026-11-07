package com.litemall.service;

import com.litemall.client.GoodsClient;
import com.litemall.domain.PO.Topic;
import com.litemall.domain.VO.TopicDetailVO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.TopicMapper;
import com.litemall.service.impl.TopicServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * R3-02 D7 专题详情契约自检（纯 Mockito，不连 MySQL，不启动 Feign / Spring）。
 * 覆盖 404、goods 串解析（JSON 数组串 + 逗号串）、脏/失效 id 跳过、免 Feign 直出空列表。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopicServiceImplTest {

    @Mock
    private TopicMapper topicMapper;

    @Mock
    private GoodsClient goodsClient;

    private TopicServiceImpl topicService;

    @BeforeEach
    void setUp() {
        topicService = new TopicServiceImpl(goodsClient);
        ReflectionTestUtils.setField(topicService, "baseMapper", topicMapper);
    }

    @Test
    @DisplayName("D7：topicId 缺失 → 400 PARAM_INVALID")
    void detail_missingId_rejected() {
        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> topicService.getTopicDetail(null)).getErrorCode());
        verifyNoInteractions(goodsClient);
    }

    @Test
    @DisplayName("D7：专题不存在 / 已逻辑删除 → 404 NotFoundException")
    void detail_notFound() {
        when(topicMapper.selectById(996001L)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> topicService.getTopicDetail(996001L));

        when(topicMapper.selectById(996002L)).thenReturn(topic(996002L).setDeleted(true));
        assertThrows(NotFoundException.class, () -> topicService.getTopicDetail(996002L));

        verifyNoInteractions(goodsClient);
    }

    @Test
    @DisplayName("D7：goods 实测为 JSON 数组串时解析成 id，出参复用 GoodsBriefDTO，脏 id 跳过")
    void detail_jsonGoodsString_parsed() {
        when(topicMapper.selectById(996001L)).thenReturn(
                topic(996001L).setGoods("[\"993005\", \"993006\", \"bad\", \"\"]"));
        when(goodsClient.listGoodsBriefByIds(List.of(993005L, 993006L)))
                .thenReturn(List.of(brief(993005L), brief(993006L)));

        TopicDetailVO vo = topicService.getTopicDetail(996001L);

        assertEquals(996001L, vo.getId().longValue());
        assertEquals("秋季上新", vo.getTitle());
        assertEquals("换季好物", vo.getSubtitle());
        assertEquals("https://cdn/t.jpg", vo.getPicUrl());
        assertEquals("<p>专题正文</p>", vo.getContent());
        assertEquals(2, vo.getGoodsList().size());
        assertEquals(993005L, vo.getGoodsList().get(0).getId().longValue());
        verify(goodsClient).listGoodsBriefByIds(List.of(993005L, 993006L));
    }

    @Test
    @DisplayName("D7：需求书描述的逗号分隔串同样兼容")
    void detail_commaGoodsString_parsed() {
        when(topicMapper.selectById(996001L)).thenReturn(topic(996001L).setGoods("993005,993006"));
        when(goodsClient.listGoodsBriefByIds(any())).thenReturn(List.of(brief(993005L), brief(993006L)));

        TopicDetailVO vo = topicService.getTopicDetail(996001L);

        assertEquals(2, vo.getGoodsList().size());
        verify(goodsClient).listGoodsBriefByIds(List.of(993005L, 993006L));
    }

    @Test
    @DisplayName("D7：goods 为空 → goodsList 空数组且不调 Feign")
    void detail_emptyGoods_noFeign() {
        when(topicMapper.selectById(996001L)).thenReturn(topic(996001L).setGoods("   "));

        TopicDetailVO vo = topicService.getTopicDetail(996001L);

        assertTrue(vo.getGoodsList().isEmpty());
        verify(goodsClient, never()).listGoodsBriefByIds(any());
    }

    @Test
    @DisplayName("D7：失效商品 id 由 Feign 侧过滤，返回 null 也不 500")
    void detail_nullBriefs_tolerated() {
        when(topicMapper.selectById(996001L)).thenReturn(topic(996001L).setGoods("993999"));
        when(goodsClient.listGoodsBriefByIds(List.of(993999L))).thenReturn(null);

        TopicDetailVO vo = topicService.getTopicDetail(996001L);

        assertTrue(vo.getGoodsList().isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    private Topic topic(Long id) {
        return new Topic()
                .setId(id)
                .setTitle("秋季上新")
                .setSubtitle("换季好物")
                .setContent("<p>专题正文</p>")
                .setPicUrl("https://cdn/t.jpg")
                .setEnabled(true)
                .setDeleted(false);
    }

    private GoodsBriefDTO brief(Long id) {
        GoodsBriefDTO dto = new GoodsBriefDTO();
        dto.setId(id);
        dto.setName("商品" + id);
        dto.setPicUrl("https://cdn/g.jpg");
        return dto;
    }
}
