package com.litemall.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.PO.Order;
import com.litemall.domain.VO.AftersaleApplyVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.service.IAftersaleService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 售后表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class AftersaleServiceImpl extends ServiceImpl<AftersaleMapper, Aftersale> implements IAftersaleService {

    /** 售后状态：1 用户已申请（pending） */
    private static final int AFTERSALE_STATUS_APPLIED = 1;

    private final OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AftersaleApplyVO applyAftersale(AftersaleApplyDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }

        // 校验订单属于当前用户且非删除
        Order order = orderMapper.selectById(dto.getOrderId().intValue());
        if (order == null || Boolean.TRUE.equals(order.getDeleted())
                || !order.getUserId().equals(userId.intValue())) {
            throw new BadRequestException("订单不存在");
        }
        // 仅已确认收货的订单可申请售后
        if (order.getOrderStatus() == null || order.getOrderStatus() != 301) {
            throw new BadRequestException("仅已收货订单可申请售后");
        }

        LocalDateTime now = LocalDateTime.now();
        Aftersale aftersale = new Aftersale()
                .setAftersaleSn(generateAftersaleSn())
                .setOrderId(order.getId())
                .setUserId(userId.intValue())
                .setType(1)
                .setReason(dto.getReason())
                .setAmount(dto.getAmount())
                .setPictures(dto.getImages() == null || dto.getImages().isEmpty()
                        ? "[]" : JSONUtil.toJsonStr(dto.getImages()))
                .setStatus(AFTERSALE_STATUS_APPLIED)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(aftersale);

        // 同步订单售后状态
        order.setAftersaleStatus(AFTERSALE_STATUS_APPLIED);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        AftersaleApplyVO vo = new AftersaleApplyVO();
        vo.setAftersaleId(aftersale.getId().longValue());
        vo.setStatus("pending");
        return vo;
    }

    /** 售后编号：AS + yyyyMMddHHmmss + 6位随机 */
    private String generateAftersaleSn() {
        return "AS" + IdUtil.getSnowflakeNextIdStr().substring(0, 18);
    }
}
