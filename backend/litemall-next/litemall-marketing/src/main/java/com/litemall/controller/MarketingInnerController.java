package com.litemall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.PO.Ad;
import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PO.Topic;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AdDTO;
import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.CouponDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.domain.dto.TopicDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.service.IAdService;
import com.litemall.service.ICouponService;
import com.litemall.service.ITopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 营销服务内部接口（供其他服务 Feign 调用, 不走网关鉴权）
 * </p>
 * <p>
 * H5：新增管理端优惠券/广告/专题的查询与写接口，admin 服务不再直写
 * {@code litemall_coupon / litemall_ad / litemall_topic}。写操作的默认值补全
 * （status=0、deleted=false、addTime/updateTime）与改造前 admin 端保持一致。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/inner/market")
@RequiredArgsConstructor
@Tag(name = "营销内部接口", description = "供服务间 Feign 调用的营销接口")
public class MarketingInnerController {

    private final ICouponService couponService;
    private final IAdService adService;
    private final ITopicService topicService;

    @Operation(summary = "优惠券抵扣试算")
    @PostMapping("/coupon/check")
    public BigDecimal checkCoupon(@RequestBody CouponCheckDTO dto) {
        return couponService.checkCoupon(dto.getCouponUserId(), dto.getGoodsAmount());
    }

    @Operation(summary = "核销优惠券")
    @PostMapping("/coupon/use")
    public void useCoupon(@RequestBody CouponUseDTO dto) {
        couponService.useCoupon(dto.getCouponUserId(), dto.getOrderId());
    }

    @Operation(summary = "回退优惠券")
    @PostMapping("/coupon/rollback")
    public void rollbackCoupon(@RequestBody CouponUseDTO dto) {
        couponService.rollbackCoupon(dto.getCouponUserId(), dto.getOrderId());
    }

    /* ==================== H5：管理端优惠券（admin 服务 Feign 调用） ==================== */

    @Operation(summary = "管理端优惠券分页列表")
    @GetMapping("/admin/coupon/list")
    public PageVO<CouponDTO> adminListCoupons(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                              @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                              @RequestParam(value = "name", required = false) String name) {
        Page<Coupon> p = couponService.lambdaQuery()
                .eq(Coupon::getDeleted, false)
                .like(StringUtils.hasText(name), Coupon::getName, name)
                .orderByDesc(Coupon::getId)
                .page(new Page<>(normalizePage(pageNum), normalizeSize(pageSize)));
        return PageVO.of(p, this::toCouponDTO);
    }

    @Operation(summary = "管理端新建优惠券")
    @PostMapping("/admin/coupon/create")
    public Integer adminCreateCoupon(@RequestBody CouponDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BadRequestException("优惠券名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon();
        coupon.setName(dto.getName());
        coupon.setDesc(dto.getDesc());
        coupon.setTag(dto.getTag());
        coupon.setTotal(dto.getTotal());
        coupon.setDiscount(dto.getDiscount());
        coupon.setMin(dto.getMin());
        coupon.setLimit(dto.getLimit());
        coupon.setType(dto.getType());
        coupon.setGoodsType(dto.getGoodsType());
        coupon.setGoodsValue(dto.getGoodsValue());
        coupon.setCode(dto.getCode());
        coupon.setTimeType(dto.getTimeType());
        coupon.setDays(dto.getDays());
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        // 与改造前 admin 端一致：新建券强制"正常可用 + 未删除"
        coupon.setStatus(0);
        coupon.setDeleted(Boolean.FALSE);
        coupon.setAddTime(now);
        coupon.setUpdateTime(now);
        couponService.save(coupon);
        return coupon.getId();
    }

    @Operation(summary = "管理端更新优惠券")
    @PutMapping("/admin/coupon/update")
    public void adminUpdateCoupon(@RequestBody CouponDTO dto) {
        Coupon exists = requireCoupon(dto);
        Coupon coupon = new Coupon();
        coupon.setId(exists.getId());
        coupon.setName(dto.getName());
        coupon.setDesc(dto.getDesc());
        coupon.setTag(dto.getTag());
        coupon.setTotal(dto.getTotal());
        coupon.setDiscount(dto.getDiscount());
        coupon.setMin(dto.getMin());
        coupon.setLimit(dto.getLimit());
        coupon.setType(dto.getType());
        coupon.setStatus(dto.getStatus());
        coupon.setGoodsType(dto.getGoodsType());
        coupon.setGoodsValue(dto.getGoodsValue());
        coupon.setCode(dto.getCode());
        coupon.setTimeType(dto.getTimeType());
        coupon.setDays(dto.getDays());
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        coupon.setUpdateTime(LocalDateTime.now());
        couponService.updateById(coupon);
    }

    @Operation(summary = "管理端删除优惠券（逻辑删除）")
    @DeleteMapping("/admin/coupon/delete")
    public void adminDeleteCoupon(@RequestParam("id") Integer id) {
        Coupon coupon = couponService.getById(requireId(id, "优惠券"));
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
            throw new BadRequestException("优惠券不存在");
        }
        Coupon update = new Coupon();
        update.setId(id);
        update.setDeleted(Boolean.TRUE);
        update.setUpdateTime(LocalDateTime.now());
        couponService.updateById(update);
    }

    /* ==================== H5：管理端广告（admin 服务 Feign 调用） ==================== */

    @Operation(summary = "管理端广告分页列表")
    @GetMapping("/admin/ad/list")
    public PageVO<AdDTO> adminListAds(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                      @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                      @RequestParam(value = "name", required = false) String name) {
        Page<Ad> p = adService.lambdaQuery()
                .eq(Ad::getDeleted, false)
                .like(StringUtils.hasText(name), Ad::getName, name)
                .orderByDesc(Ad::getId)
                .page(new Page<>(normalizePage(pageNum), normalizeSize(pageSize)));
        return PageVO.of(p, this::toAdDTO);
    }

    @Operation(summary = "管理端新建广告")
    @PostMapping("/admin/ad/create")
    public Integer adminCreateAd(@RequestBody AdDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BadRequestException("广告名称不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        Ad ad = new Ad();
        ad.setName(dto.getName());
        ad.setLink(dto.getLink());
        ad.setUrl(dto.getUrl());
        ad.setPosition(dto.getPosition());
        ad.setContent(dto.getContent());
        ad.setStartTime(dto.getStartTime());
        ad.setEndTime(dto.getEndTime());
        ad.setEnabled(dto.getEnabled());
        ad.setDeleted(Boolean.FALSE);
        ad.setAddTime(now);
        ad.setUpdateTime(now);
        adService.save(ad);
        return ad.getId();
    }

    @Operation(summary = "管理端更新广告")
    @PutMapping("/admin/ad/update")
    public void adminUpdateAd(@RequestBody AdDTO dto) {
        Ad exists = requireAd(dto);
        Ad ad = new Ad();
        ad.setId(exists.getId());
        ad.setName(dto.getName());
        ad.setLink(dto.getLink());
        ad.setUrl(dto.getUrl());
        ad.setPosition(dto.getPosition());
        ad.setContent(dto.getContent());
        ad.setStartTime(dto.getStartTime());
        ad.setEndTime(dto.getEndTime());
        ad.setEnabled(dto.getEnabled());
        ad.setUpdateTime(LocalDateTime.now());
        adService.updateById(ad);
    }

    @Operation(summary = "管理端删除广告（逻辑删除）")
    @DeleteMapping("/admin/ad/delete")
    public void adminDeleteAd(@RequestParam("id") Integer id) {
        Ad ad = adService.getById(requireId(id, "广告"));
        if (ad == null || Boolean.TRUE.equals(ad.getDeleted())) {
            throw new BadRequestException("广告不存在");
        }
        Ad update = new Ad();
        update.setId(id);
        update.setDeleted(Boolean.TRUE);
        update.setUpdateTime(LocalDateTime.now());
        adService.updateById(update);
    }

    /* ==================== H5：管理端专题（admin 服务 Feign 调用） ==================== */

    @Operation(summary = "管理端专题分页列表")
    @GetMapping("/admin/topic/list")
    public PageVO<TopicDTO> adminListTopics(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                            @RequestParam(value = "title", required = false) String title) {
        Page<Topic> p = topicService.lambdaQuery()
                .eq(Topic::getDeleted, false)
                .like(StringUtils.hasText(title), Topic::getTitle, title)
                .orderByDesc(Topic::getId)
                .page(new Page<>(normalizePage(pageNum), normalizeSize(pageSize)));
        return PageVO.of(p, this::toTopicDTO);
    }

    @Operation(summary = "管理端新建专题")
    @PostMapping("/admin/topic/create")
    public Integer adminCreateTopic(@RequestBody TopicDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTitle())) {
            throw new BadRequestException("专题标题不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        Topic topic = new Topic();
        topic.setTitle(dto.getTitle());
        topic.setSubtitle(dto.getSubtitle());
        topic.setContent(dto.getContent());
        topic.setPrice(dto.getPrice());
        topic.setReadCount(dto.getReadCount());
        topic.setPicUrl(dto.getPicUrl());
        topic.setSortOrder(dto.getSortOrder());
        topic.setDeleted(Boolean.FALSE);
        topic.setAddTime(now);
        topic.setUpdateTime(now);
        topicService.save(topic);
        return topic.getId();
    }

    @Operation(summary = "管理端更新专题")
    @PutMapping("/admin/topic/update")
    public void adminUpdateTopic(@RequestBody TopicDTO dto) {
        Topic exists = requireTopic(dto);
        Topic topic = new Topic();
        topic.setId(exists.getId());
        topic.setTitle(dto.getTitle());
        topic.setSubtitle(dto.getSubtitle());
        topic.setContent(dto.getContent());
        topic.setPrice(dto.getPrice());
        topic.setReadCount(dto.getReadCount());
        topic.setPicUrl(dto.getPicUrl());
        topic.setSortOrder(dto.getSortOrder());
        topic.setUpdateTime(LocalDateTime.now());
        topicService.updateById(topic);
    }

    @Operation(summary = "管理端删除专题（逻辑删除）")
    @DeleteMapping("/admin/topic/delete")
    public void adminDeleteTopic(@RequestParam("id") Integer id) {
        Topic topic = topicService.getById(requireId(id, "专题"));
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) {
            throw new BadRequestException("专题不存在");
        }
        Topic update = new Topic();
        update.setId(id);
        update.setDeleted(Boolean.TRUE);
        update.setUpdateTime(LocalDateTime.now());
        topicService.updateById(update);
    }

    /* ==================== 私有方法 ==================== */

    private int normalizePage(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizeSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
    }

    private Integer requireId(Integer id, String label) {
        if (id == null) {
            throw new BadRequestException(label + "ID不能为空");
        }
        return id;
    }

    private Coupon requireCoupon(CouponDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BadRequestException("优惠券ID不能为空");
        }
        Coupon coupon = couponService.getById(dto.getId());
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
            throw new BadRequestException("优惠券不存在");
        }
        return coupon;
    }

    private Ad requireAd(AdDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BadRequestException("广告ID不能为空");
        }
        Ad ad = adService.getById(dto.getId());
        if (ad == null || Boolean.TRUE.equals(ad.getDeleted())) {
            throw new BadRequestException("广告不存在");
        }
        return ad;
    }

    private Topic requireTopic(TopicDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BadRequestException("专题ID不能为空");
        }
        Topic topic = topicService.getById(dto.getId());
        if (topic == null || Boolean.TRUE.equals(topic.getDeleted())) {
            throw new BadRequestException("专题不存在");
        }
        return topic;
    }

    private CouponDTO toCouponDTO(Coupon coupon) {
        CouponDTO dto = new CouponDTO();
        dto.setId(coupon.getId());
        dto.setName(coupon.getName());
        dto.setDesc(coupon.getDesc());
        dto.setTag(coupon.getTag());
        dto.setTotal(coupon.getTotal());
        dto.setDiscount(coupon.getDiscount());
        dto.setMin(coupon.getMin());
        dto.setLimit(coupon.getLimit());
        dto.setType(coupon.getType());
        dto.setStatus(coupon.getStatus());
        dto.setGoodsType(coupon.getGoodsType());
        dto.setGoodsValue(coupon.getGoodsValue());
        dto.setCode(coupon.getCode());
        dto.setTimeType(coupon.getTimeType());
        dto.setDays(coupon.getDays());
        dto.setStartTime(coupon.getStartTime());
        dto.setEndTime(coupon.getEndTime());
        dto.setAddTime(coupon.getAddTime());
        dto.setUpdateTime(coupon.getUpdateTime());
        dto.setDeleted(coupon.getDeleted());
        return dto;
    }

    private AdDTO toAdDTO(Ad ad) {
        AdDTO dto = new AdDTO();
        dto.setId(ad.getId());
        dto.setName(ad.getName());
        dto.setLink(ad.getLink());
        dto.setUrl(ad.getUrl());
        dto.setPosition(ad.getPosition());
        dto.setContent(ad.getContent());
        dto.setStartTime(ad.getStartTime());
        dto.setEndTime(ad.getEndTime());
        dto.setEnabled(ad.getEnabled());
        dto.setAddTime(ad.getAddTime());
        dto.setUpdateTime(ad.getUpdateTime());
        dto.setDeleted(ad.getDeleted());
        return dto;
    }

    private TopicDTO toTopicDTO(Topic topic) {
        TopicDTO dto = new TopicDTO();
        dto.setId(topic.getId());
        dto.setTitle(topic.getTitle());
        dto.setSubtitle(topic.getSubtitle());
        dto.setContent(topic.getContent());
        dto.setPrice(topic.getPrice());
        dto.setReadCount(topic.getReadCount());
        dto.setPicUrl(topic.getPicUrl());
        dto.setSortOrder(topic.getSortOrder());
        dto.setAddTime(topic.getAddTime());
        dto.setUpdateTime(topic.getUpdateTime());
        dto.setDeleted(topic.getDeleted());
        return dto;
    }
}