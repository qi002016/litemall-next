package com.litemall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.domain.PO.Topic;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.TopicDetailVO;
import com.litemall.domain.VO.TopicVO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.TopicMapper;
import com.litemall.service.ITopicService;
import com.litemall.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements ITopicService {

    /** 商品信息只能经 Feign 拿（§3.3：禁止跨库直连别的服务表） */
    private final GoodsClient goodsClient;

    @Override
    public PageVO<TopicVO> getTopicList(int pageNum, int pageSize) {
        Page<Topic> page = new Page<>(pageNum, pageSize);
        Page<Topic> p = lambdaQuery()
                .eq(Topic::getDeleted, false)
                // R2：C 端只展示启用中的专题，管理端停用（enabled=false）后即刻不可见
                .eq(Topic::getEnabled, true)
                .orderByAsc(Topic::getSortOrder)
                .orderByDesc(Topic::getAddTime)
                .page(page);
        List<TopicVO> voList = BeanUtils.copyList(p.getRecords(), TopicVO.class);
        return new PageVO<>(p.getTotal(), p.getPages(), voList);
    }

    @Override
    public TopicDetailVO getTopicDetail(Long topicId) {
        if (topicId == null) {
            throw new BadRequestException("专题ID不能为空", BadRequestException.PARAM_INVALID);
        }
        // getById 不带 deleted 条件，已逻辑删除的专题要在这里显式判掉
        Topic topic = getById(topicId);
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) {
            throw new NotFoundException("专题不存在");
        }
        List<Long> goodsIds = parseGoodsIds(topic.getGoods());
        // /inner/goods/brief/list 会过滤掉已删除/不存在的商品，失效 id 自然跳过，不会把详情打成 500
        List<GoodsBriefDTO> goodsList = goodsIds.isEmpty()
                ? List.of()
                : goodsClient.listGoodsBriefByIds(goodsIds);

        TopicDetailVO vo = new TopicDetailVO();
        vo.setId(topic.getId());
        vo.setTitle(topic.getTitle());
        vo.setSubtitle(topic.getSubtitle());
        vo.setPicUrl(topic.getPicUrl());
        vo.setContent(topic.getContent());
        vo.setGoodsList(goodsList == null ? List.of() : goodsList);
        return vo;
    }

    /**
     * 演示库 litemall_topic.goods 实测是 '["993005","993006"]' 这样的 JSON 数组串，
     * 而需求书描述为逗号分隔 ID 串；两种都兼容，避免格式偏差让专题整页拿不到商品。
     * 单个非数字脏项直接跳过，不让整页 500。
     */
    private List<Long> parseGoodsIds(String goods) {
        if (!StringUtils.hasText(goods)) {
            return List.of();
        }
        String raw = goods.trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        List<Long> ids = new ArrayList<>();
        for (String token : raw.split(",")) {
            String id = token.trim().replace("\"", "").replace("'", "").trim();
            if (!StringUtils.hasText(id)) {
                continue;
            }
            try {
                ids.add(Long.valueOf(id));
            } catch (NumberFormatException ignored) {
                // 脏 id 跳过，不影响其余商品
            }
        }
        return ids;
    }
}
