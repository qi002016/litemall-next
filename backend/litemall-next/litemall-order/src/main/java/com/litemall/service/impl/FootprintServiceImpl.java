package com.litemall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.domain.PO.Footprint;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.FootprintVO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.mapper.FootprintMapper;
import com.litemall.service.IFootprintService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户浏览足迹表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class FootprintServiceImpl extends ServiceImpl<FootprintMapper, Footprint> implements IFootprintService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GoodsClient goodsClient;

    @Override
    public PageVO<FootprintVO> listFootprint(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUser();
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Footprint> page = new Page<>(pn, ps);
        Page<Footprint> footprintPage = lambdaQuery()
                .eq(Footprint::getUserId, userId.intValue())
                .eq(Footprint::getDeleted, false)
                .orderByDesc(Footprint::getAddTime)
                .page(page);

        List<Footprint> records = footprintPage.getRecords();
        if (records.isEmpty()) {
            return new PageVO<>(footprintPage.getTotal(), footprintPage.getPages(), List.of());
        }

        List<Long> goodsIds = records.stream()
                .map(f -> f.getGoodsId().longValue())
                .collect(Collectors.toList());
        List<GoodsBriefDTO> briefList = goodsClient.listGoodsBriefByIds(goodsIds);
        Map<Long, GoodsBriefDTO> briefMap = briefList.stream()
                .collect(Collectors.toMap(GoodsBriefDTO::getId, Function.identity(), (a, b) -> a));

        List<FootprintVO> voList = records.stream().map(f -> {
            FootprintVO vo = new FootprintVO();
            vo.setId(f.getId().longValue());
            vo.setGoodsId(f.getGoodsId().longValue());
            GoodsBriefDTO brief = briefMap.get(f.getGoodsId().longValue());
            if (brief != null) {
                vo.setGoodsName(brief.getName());
                vo.setPicUrl(brief.getPicUrl());
            }
            vo.setAddTime(f.getAddTime() == null ? null : f.getAddTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(footprintPage.getTotal(), footprintPage.getPages(), voList);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void addFootprint(Long goodsId) {
        Long userId = UserContext.getUser();
        if (userId == null || goodsId == null || goodsId <= 0) {
            return;
        }
        // 校验商品存在
        List<GoodsBriefDTO> briefList = goodsClient.listGoodsBriefByIds(List.of(goodsId));
        if (briefList.isEmpty()) {
            return;
        }
        // 同商品保留最近一次：删除旧记录后插入新记录
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Footprint> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Footprint>()
                        .eq(Footprint::getUserId, userId.intValue())
                        .eq(Footprint::getGoodsId, goodsId.intValue());
        remove(wrapper);
        Footprint footprint = new Footprint()
                .setUserId(userId.intValue())
                .setGoodsId(goodsId.intValue())
                .setAddTime(java.time.LocalDateTime.now())
                .setUpdateTime(java.time.LocalDateTime.now())
                .setDeleted(false);
        save(footprint);
    }
}
