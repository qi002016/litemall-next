package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.CouponUserVO;
import com.litemall.domain.VO.CouponVO;
import com.litemall.domain.VO.UsableCouponsVO;

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
     * 领取优惠券（CI-54：同步返回四态，不是排队）
     * <p>
     * 判定顺序见 05 §2.4：登录 → 券存在 → 已领过 → 频控 → 不可领取/不在有效期 → 已领完 → 出券。
     * 四态失败以返回值表达（{@code result=already_received/sold_out/disabled}），
     * 未登录/券不存在/频控仍以异常表达（data=null）。
     * </p>
     *
     * @param couponId 优惠券ID
     * @return 四态结果；success 时带 couponUser
     */
    CouponReceiveResultVO receiveCoupon(Long couponId);

    /**
     * 查询领券结果（CI-55 客户端轮询；R2 领券已同步，本接口仅作兼容读法）
     */
    CouponReceiveResultVO getReceiveResult(Long couponId);

    /**
     * 获取用户优惠券列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param status 优惠券状态
     * @return 优惠券用户列表
     */
    PageVO<CouponUserVO> getCouponUserList(Integer pageNum, Integer pageSize, String status);

    /**
     * 可用券试算（CN-19，C 端只读，不占券）
     * <p>
     * 按「我的券」逐张判定在 goodsAmount 下能否使用，并给出不可用原因与本次可抵扣金额；
     * 排序为「可用在前 → 券面额降序 → 到期时间升序」。
     * </p>
     *
     * @param goodsAmountCents 商品金额（整数分，不含运费，≥0）
     * @return 试算结果；无券时 coupons 为 []
     */
    UsableCouponsVO getUsableCoupons(Integer goodsAmountCents);

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
