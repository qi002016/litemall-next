package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.CollectAddDTO;
import com.litemall.domain.PO.Collect;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CollectVO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CollectMapper;
import com.litemall.service.ICollectService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 收藏表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class CollectServiceImpl extends ServiceImpl<CollectMapper, Collect> implements ICollectService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 收藏类型：0 商品 */
    private static final int TYPE_GOODS = 0;

    private final GoodsClient goodsClient;

    @Override
    public PageVO<CollectVO> listCollect(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUser();
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Collect> page = new Page<>(pn, ps);
        Page<Collect> collectPage = lambdaQuery()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getType, TYPE_GOODS)
                .eq(Collect::getDeleted, false)
                .orderByDesc(Collect::getAddTime)
                .page(page);

        List<Collect> records = collectPage.getRecords();
        if (records.isEmpty()) {
            return new PageVO<>(collectPage.getTotal(), collectPage.getPages(), List.of());
        }

        // 批量查商品信息（Feign）
        List<Long> goodsIds = records.stream()
                .map(c -> c.getValueId())
                .collect(Collectors.toList());
        List<GoodsBriefDTO> briefList = goodsClient.listGoodsBriefByIds(goodsIds);
        Map<Long, GoodsBriefDTO> briefMap = briefList.stream()
                .collect(Collectors.toMap(GoodsBriefDTO::getId, Function.identity(), (a, b) -> a));

        List<CollectVO> voList = records.stream().map(c -> {
            CollectVO vo = new CollectVO();
            vo.setId(c.getId());
            vo.setGoodsId(c.getValueId());
            GoodsBriefDTO brief = briefMap.get(c.getValueId());
            if (brief != null) {
                vo.setGoodsName(brief.getName());
                vo.setPicUrl(brief.getPicUrl());
            }
            vo.setAddTime(c.getAddTime() == null ? null : c.getAddTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(collectPage.getTotal(), collectPage.getPages(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCollect(CollectAddDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }

        Collect exist = lambdaQuery()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getValueId, dto.getGoodsId())
                .eq(Collect::getType, TYPE_GOODS)
                .one();

        LocalDateTime now = LocalDateTime.now();
        if (dto.getType() == 0) {
            // 收藏
            if (exist == null) {
                Collect collect = new Collect()
                        .setUserId(userId)
                        .setValueId(dto.getGoodsId())
                        .setType(TYPE_GOODS)
                        .setAddTime(now)
                        .setUpdateTime(now)
                        .setDeleted(false);
                save(collect);
            } else if (Boolean.TRUE.equals(exist.getDeleted())) {
                exist.setDeleted(false);
                exist.setUpdateTime(now);
                updateById(exist);
            }
            return true;
        } else {
            // 取消收藏（逻辑删除）
            if (exist != null && !Boolean.TRUE.equals(exist.getDeleted())) {
                exist.setDeleted(true);
                exist.setUpdateTime(now);
                updateById(exist);
            }
            return false;
        }
    }
}
