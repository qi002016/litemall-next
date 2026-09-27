package com.litemall.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.GrouponRule;
import com.litemall.domain.VO.GrouponRuleVO;
import com.litemall.mapper.GrouponRuleMapper;
import com.litemall.service.IGrouponRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 团购规则表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Service
@RequiredArgsConstructor
public class GrouponRuleServiceImpl extends ServiceImpl<GrouponRuleMapper, GrouponRule> implements IGrouponRuleService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageVO<GrouponRuleVO> getGrouponList(Integer pageNum, Integer pageSize) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;

        LocalDateTime now = LocalDateTime.now();
        Page<GrouponRule> page = lambdaQuery()
                .eq(GrouponRule::getDeleted, false)
                .eq(GrouponRule::getStatus, 0)
                .and(w -> w.isNull(GrouponRule::getExpireTime).or().gt(GrouponRule::getExpireTime, now))
                .orderByDesc(GrouponRule::getAddTime)
                .page(new Page<>(pn, ps));

        return PageVO.of(page, r -> {
            GrouponRuleVO vo = new GrouponRuleVO();
            vo.setId(r.getId());
            vo.setGoodsId(r.getGoodsId());
            vo.setGoodsName(r.getGoodsName());
            vo.setPicUrl(r.getPicUrl());
            vo.setDiscount(r.getDiscount());
            vo.setDiscountMember(r.getDiscountMember());
            vo.setExpireTime(r.getExpireTime() == null ? null : r.getExpireTime().format(DT));
            return vo;
        });
    }
}
