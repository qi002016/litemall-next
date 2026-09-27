package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.CouponUserVO;
import com.litemall.domain.VO.CouponVO;

import java.math.BigDecimal;

/**
 * <p>
 * 优惠券信息及规则表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
public interface ICouponService extends IService<Coupon> {

    /**
     * 获取优惠券列表
     * @return 优惠券列表
     */
    PageVO<CouponVO> getCouponList(Integer pageNum, Integer pageSize);

    /**
     * 领取优惠券（异步：Redis 预减库存 + MQ 落库）
     * @param couponId 优惠券ID
     * @return 排队状态 PROCESSING
     */
    CouponReceiveResultVO receiveCoupon(Long couponId);

    /**
     * 查询领券结果（客户端轮询）
     */
    CouponReceiveResultVO getReceiveResult(Long couponId);

    /**
     * MQ 消费：实际落库（内部方法，由 consumer 调用）
     */
    void persistReceive(Long couponId, Long userId);

    /**
     * 获取用户优惠券列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param status 优惠券状态
     * @return 优惠券用户列表
     */
    PageVO<CouponUserVO> getCouponUserList(Integer pageNum, Integer pageSize, String status);

    /**
     * 优惠券抵扣试算（内部接口，供订单服务调用）
     * @param couponUserId 用户券记录ID
     * @param goodsAmount 商品总额
     * @return 可抵扣金额；券无效/未使用/未满足门槛返回 0
     */
    BigDecimal checkCoupon(Long couponUserId, BigDecimal goodsAmount);

    /**
     * 核销优惠券（内部接口，提交订单时调用）
     * @param couponUserId 用户券记录ID
     * @param orderId 订单ID
     */
    void useCoupon(Long couponUserId, Long orderId);

    /**
     * 回退优惠券（内部接口，取消订单时调用）
     * @param couponUserId 用户券记录ID（可为 null，此时按 orderId 反查）
     * @param orderId 订单ID
     */
    void rollbackCoupon(Long couponUserId, Long orderId);
}
