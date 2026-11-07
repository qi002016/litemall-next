package com.litemall.controller;

import com.litemall.client.MarketingClient;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.AdDTO;
import com.litemall.domain.dto.CouponDTO;
import com.litemall.domain.dto.TopicDTO;
import com.litemall.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端营销接口（优惠券 / 广告 / 专题）。
 * </p>
 * <p>
 * H5：不再持有 {@code AdminCouponMapper/AdminAdMapper/AdminTopicMapper}，
 * 所有读写经 {@link MarketingClient} 调用 marketing 服务的
 * {@code /inner/market/admin/**}。对外路径/方法/字段与改造前一致。
 * </p>
 */
@RestController
@RequestMapping("/admin/market")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingClient marketingClient;

    /* ---------- 优惠券 ---------- */

    @GetMapping("/coupon/list")
    public Result<PageVO<CouponDTO>> couponList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(marketingClient.adminListCoupons(pageNum, pageSize, name));
    }

    @PostMapping("/coupon/create")
    public Result<Void> couponCreate(@RequestBody CouponDTO coupon) {
        marketingClient.adminCreateCoupon(coupon);
        return Result.ok();
    }

    @PutMapping("/coupon/update")
    public Result<Void> couponUpdate(@RequestBody CouponDTO coupon) {
        marketingClient.adminUpdateCoupon(coupon);
        return Result.ok();
    }

    @DeleteMapping("/coupon/delete")
    public Result<Void> couponDelete(@RequestParam Long id) {
        marketingClient.adminDeleteCoupon(id);
        return Result.ok();
    }

    /* ---------- 广告 ---------- */

    @GetMapping("/ad/list")
    public Result<PageVO<AdDTO>> adList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name) {
        return Result.ok(marketingClient.adminListAds(pageNum, pageSize, name));
    }

    @PostMapping("/ad/create")
    public Result<Void> adCreate(@RequestBody AdDTO ad) {
        normalizeAdForWrite(ad);
        marketingClient.adminCreateAd(ad);
        return Result.ok();
    }

    @PutMapping("/ad/update")
    public Result<Void> adUpdate(@RequestBody AdDTO ad) {
        normalizeAdForWrite(ad);
        marketingClient.adminUpdateAd(ad);
        return Result.ok();
    }

    @DeleteMapping("/ad/delete")
    public Result<Void> adDelete(@RequestParam Long id) {
        marketingClient.adminDeleteAd(id);
        return Result.ok();
    }

    /* ---------- 专题 ---------- */

    @GetMapping("/topic/list")
    public Result<PageVO<TopicDTO>> topicList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title) {
        return Result.ok(marketingClient.adminListTopics(pageNum, pageSize, title));
    }

    @PostMapping("/topic/create")
    public Result<Void> topicCreate(@RequestBody TopicDTO topic) {
        marketingClient.adminCreateTopic(topic);
        return Result.ok();
    }

    @PutMapping("/topic/update")
    public Result<Void> topicUpdate(@RequestBody TopicDTO topic) {
        marketingClient.adminUpdateTopic(topic);
        return Result.ok();
    }

    @DeleteMapping("/topic/delete")
    public Result<Void> topicDelete(@RequestParam Long id) {
        marketingClient.adminDeleteTopic(id);
        return Result.ok();
    }

    /**
     * 广告写入口的跳转参数校验放在 Controller（§3.3：Controller 只做参数校验与装配），
     * 让 /admin/** 直接拿到 400 PARAM_INVALID，不必绕一圈 Feign 再解码；
     * marketing 服务内还有一层同规则校验兜底，防止其它服务直调 /inner/** 绕过。
     * AdDTO 新增的 linkType/sortOrder 由 Jackson 原样透传，无需在这里逐字段搬运。
     */
    private void normalizeAdForWrite(AdDTO ad) {
        if (ad == null || ad.getLinkType() == null) {
            return;
        }
        int linkType = ad.getLinkType();
        if (linkType != 0 && linkType != 1 && linkType != 2) {
            throw new BadRequestException("跳转类型只能是 0/1/2", BadRequestException.PARAM_INVALID);
        }
        if (linkType == 0) {
            // 无跳转：清掉脏链接，避免把小程序路径带进库（D4/D8）
            ad.setLink(null);
            return;
        }
        String link = ad.getLink() == null ? null : ad.getLink().trim();
        if (!StringUtils.hasText(link) || !link.matches("\\d+")) {
            throw new BadRequestException("跳转目标ID必须是非空数字", BadRequestException.PARAM_INVALID);
        }
        ad.setLink(link);
    }
}
