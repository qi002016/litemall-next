package com.litemall.controller;

import com.litemall.domain.DTO.CouponReceiveDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdVO;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.CouponUserVO;
import com.litemall.domain.VO.CouponVO;
import com.litemall.domain.VO.NoticeVO;
import com.litemall.domain.VO.TopicVO;
import com.litemall.domain.VO.UsableCouponsVO;
import com.litemall.service.IAdService;
import com.litemall.service.ICouponService;
import com.litemall.service.INoticeService;
import com.litemall.service.ITopicService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/app/market")
@Tag(name = "market", description = "市场活动相关接口")
@RequiredArgsConstructor
public class MarketingController {

    private final IAdService adService;
    private final ICouponService couponService;
    private final ITopicService topicService;
    private final INoticeService noticeService;


    @GetMapping("/ad/list")
    public Result<List<AdVO>> getAdList(String position) {
        return Result.ok(adService.getAdList(position));
    }

    @GetMapping("/coupon/list")
    public Result<PageVO<CouponVO>> getCouponList(Integer pageNum, Integer pageSize) {
        return Result.ok(couponService.getCouponList(pageNum, pageSize));
    }

    /**
     * CI-54 领券（同步四态）。
     * <p>
     * 失败分支的 {@code data} 必须是 {@link CouponReceiveResultVO} 本身（{@code result/message}），
     * 这是 R1 §4.1「失败 data 只允许 null 或 {@code {"errorCode"}}」的显式例外 E-1：若用抛异常表达
     * 四态，{@code CommonExceptionAdvice} 会把 data 变成 {@code {"errorCode"}}，前端就分不清
     * already_received / sold_out / disabled。因此这里手工构造 {@code Result}：
     * 成功 200「领取成功」，其余三态 400 且 msg=data.message。
     * </p>
     */
    @PostMapping("/coupon/receive")
    public Result<CouponReceiveResultVO> receiveCoupon(@Validated @RequestBody CouponReceiveDTO dto) {
        CouponReceiveResultVO vo = couponService.receiveCoupon(dto.getCouponId());
        if (CouponReceiveResultVO.RESULT_SUCCESS.equals(vo.getResult())) {
            return new Result<>(200, vo.getMessage(), vo);
        }
        return new Result<>(400, vo.getMessage(), vo);
    }

    @GetMapping("/coupon/receive/result")
    public Result<CouponReceiveResultVO> getReceiveResult(Long couponId) {
        return Result.ok(couponService.getReceiveResult(couponId));
    }

    @GetMapping("/coupon/mylist")
    public Result<PageVO<CouponUserVO>> getCouponUserList(Integer pageNum, Integer pageSize, String status) {
        return Result.ok(couponService.getCouponUserList(pageNum, pageSize, status));
    }

    /**
     * CN-19：可用券试算（只读，不占券）。
     * <p>
     * goodsAmount 为商品金额（整数分，不含运费）；缺失/负数 → 400 PARAM_INVALID。
     * </p>
     */
    @GetMapping("/coupon/usable")
    public Result<UsableCouponsVO> getUsableCoupons(
            @RequestParam(value = "goodsAmount", required = false) Integer goodsAmount) {
        return Result.ok(couponService.getUsableCoupons(goodsAmount));
    }

    @GetMapping("/topic/list")
    public Result<PageVO<TopicVO>> getTopicList(Integer pageNum, Integer pageSize) {
        return Result.ok(topicService.getTopicList(pageNum, pageSize));
    }

    @GetMapping("/notice/list")
    public Result<PageVO<NoticeVO>> getNoticeList(Integer pageNum, Integer pageSize) {
        return Result.ok(noticeService.getNoticeList(pageNum, pageSize));
    }
}
