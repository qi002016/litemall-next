package com.litemall.controller;

import com.litemall.domain.DTO.CouponReceiveDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdVO;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.CouponUserVO;
import com.litemall.domain.VO.CouponVO;
import com.litemall.domain.VO.GrouponRuleVO;
import com.litemall.domain.VO.NoticeVO;
import com.litemall.domain.VO.TopicVO;
import com.litemall.service.IAdService;
import com.litemall.service.ICouponService;
import com.litemall.service.IGrouponRuleService;
import com.litemall.service.INoticeService;
import com.litemall.service.ITopicService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/app/market")
@Tag(name = "market", description = "市场活动相关接口")
@RequiredArgsConstructor
public class MarketingController {

    private final IAdService adService;
    private final ICouponService couponService;
    private final IGrouponRuleService grouponRuleService;
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

    @PostMapping("/coupon/receive")
    public Result<CouponReceiveResultVO> receiveCoupon(@Validated @RequestBody CouponReceiveDTO dto) {
        return Result.ok(couponService.receiveCoupon(dto.getCouponId()));
    }

    @GetMapping("/coupon/receive/result")
    public Result<CouponReceiveResultVO> getReceiveResult(Long couponId) {
        return Result.ok(couponService.getReceiveResult(couponId));
    }

    @GetMapping("/coupon/mylist")
    public Result<PageVO<CouponUserVO>> getCouponUserList(Integer pageNum, Integer pageSize, String status) {
        return Result.ok(couponService.getCouponUserList(pageNum, pageSize, status));
    }

    @GetMapping("/groupon/list")
    public Result<PageVO<GrouponRuleVO>> getGrouponList(Integer pageNum, Integer pageSize) {
        return Result.ok(grouponRuleService.getGrouponList(pageNum, pageSize));
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
