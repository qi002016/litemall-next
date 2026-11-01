package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AdDTO;
import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.CouponDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.domain.dto.NoticeAdminDTO;
import com.litemall.domain.dto.NoticeDTO;
import com.litemall.domain.dto.TopicDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * <p>
 * 营销服务 Feign 客户端
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@FeignClient(name = "litemall-marketing")
public interface MarketingClient {

    /**
     * 优惠券抵扣试算（订单确认页 / 提交订单）
     *
     * @param dto couponUserId + goodsAmount
     * @return 可抵扣金额；券无效或未使用返回 0
     */
    @PostMapping("/inner/market/coupon/check")
    BigDecimal checkCoupon(@RequestBody CouponCheckDTO dto);

    /**
     * 核销优惠券（提交订单）
     *
     * @param dto couponUserId + orderId
     */
    @PostMapping("/inner/market/coupon/use")
    void useCoupon(@RequestBody CouponUseDTO dto);

    /**
     * 回退优惠券（取消订单）
     *
     * @param dto couponUserId
     */
    @PostMapping("/inner/market/coupon/rollback")
    void rollbackCoupon(@RequestBody CouponUseDTO dto);

    /* ==================== H5：管理端营销（admin 服务经 Feign 调用，不再直写 coupon/ad/topic 表） ==================== */

    /**
     * 管理端优惠券分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param name     优惠券名称模糊匹配，可空
     * @return 分页优惠券
     */
    @GetMapping("/inner/market/admin/coupon/list")
    PageVO<CouponDTO> adminListCoupons(@RequestParam("pageNum") Integer pageNum,
                                       @RequestParam("pageSize") Integer pageSize,
                                       @RequestParam(value = "name", required = false) String name);

    /**
     * 管理端新建优惠券（status/deleted/时间由 marketing 服务补全）
     *
     * @param dto 优惠券信息
     * @return 新建优惠券ID
     */
    @PostMapping("/inner/market/admin/coupon/create")
    Long adminCreateCoupon(@RequestBody CouponDTO dto);

    /**
     * 管理端更新优惠券（仅更新非空字段）
     *
     * @param dto 优惠券信息（id 必填）
     */
    @PutMapping("/inner/market/admin/coupon/update")
    void adminUpdateCoupon(@RequestBody CouponDTO dto);

    /**
     * 管理端逻辑删除优惠券
     *
     * @param id 优惠券ID
     */
    @DeleteMapping("/inner/market/admin/coupon/delete")
    void adminDeleteCoupon(@RequestParam("id") Long id);

    /**
     * 管理端广告分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param name     广告名称模糊匹配，可空
     * @return 分页广告
     */
    @GetMapping("/inner/market/admin/ad/list")
    PageVO<AdDTO> adminListAds(@RequestParam("pageNum") Integer pageNum,
                               @RequestParam("pageSize") Integer pageSize,
                               @RequestParam(value = "name", required = false) String name);

    /**
     * 管理端新建广告
     *
     * @param dto 广告信息
     * @return 新建广告ID
     */
    @PostMapping("/inner/market/admin/ad/create")
    Long adminCreateAd(@RequestBody AdDTO dto);

    /**
     * 管理端更新广告（仅更新非空字段）
     *
     * @param dto 广告信息（id 必填）
     */
    @PutMapping("/inner/market/admin/ad/update")
    void adminUpdateAd(@RequestBody AdDTO dto);

    /**
     * 管理端逻辑删除广告
     *
     * @param id 广告ID
     */
    @DeleteMapping("/inner/market/admin/ad/delete")
    void adminDeleteAd(@RequestParam("id") Long id);

    /**
     * 管理端专题分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param title    专题标题模糊匹配，可空
     * @return 分页专题
     */
    @GetMapping("/inner/market/admin/topic/list")
    PageVO<TopicDTO> adminListTopics(@RequestParam("pageNum") Integer pageNum,
                                     @RequestParam("pageSize") Integer pageSize,
                                     @RequestParam(value = "title", required = false) String title);

    /**
     * 管理端新建专题
     *
     * @param dto 专题信息
     * @return 新建专题ID
     */
    @PostMapping("/inner/market/admin/topic/create")
    Long adminCreateTopic(@RequestBody TopicDTO dto);

    /**
     * 管理端更新专题（仅更新非空字段）
     *
     * @param dto 专题信息（id 必填）
     */
    @PutMapping("/inner/market/admin/topic/update")
    void adminUpdateTopic(@RequestBody TopicDTO dto);

    /**
     * 管理端逻辑删除专题
     *
     * @param id 专题ID
     */
    @DeleteMapping("/inner/market/admin/topic/delete")
    void adminDeleteTopic(@RequestParam("id") Long id);

    /* ==================== R2：管理端公告（CN-IN-05/06/07，marketing 拥有 litemall_notice） ==================== */

    /**
     * CN-IN-05：管理端公告分页列表（排序固定 add_time DESC, id DESC）。
     *
     * @param pageNum  页码（≥1）
     * @param pageSize 每页条数
     * @param keyword  标题模糊关键字（≤50）
     * @param enabled  启用状态筛选（可空）
     * @return 分页公告（adminName 恒 null）
     */
    @GetMapping("/inner/market/admin/notice/list")
    PageVO<NoticeAdminDTO> adminListNotices(@RequestParam("pageNum") Integer pageNum,
                                            @RequestParam("pageSize") Integer pageSize,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "enabled", required = false) Boolean enabled);

    /**
     * CN-IN-06：新建公告。
     *
     * @param dto 公告信息（不得含 id；adminId 由 admin 透传）
     * @return 新建公告ID
     */
    @PostMapping("/inner/market/admin/notice/create")
    Long adminCreateNotice(@RequestBody NoticeDTO dto);

    /**
     * CN-IN-07：编辑/启停公告（CAS 于 id + update_time）。
     *
     * @param dto 公告信息（id 必填，其余可选）
     * @return 公告ID
     */
    @PostMapping("/inner/market/admin/notice/update")
    Long adminUpdateNotice(@RequestBody NoticeDTO dto);
}
