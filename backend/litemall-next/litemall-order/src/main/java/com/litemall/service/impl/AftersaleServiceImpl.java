package com.litemall.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.PayClient;
import com.litemall.constant.AftersaleStatus;
import com.litemall.constant.OrderStatus;
import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.DTO.AftersaleReturnShipDTO;
import com.litemall.domain.DTO.RefundBriefDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.PO.OrderLog;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.AftersaleApplyVO;
import com.litemall.domain.VO.AftersaleDetailVO;
import com.litemall.domain.VO.AftersaleListItemVO;
import com.litemall.domain.VO.AftersaleReturnShipVO;
import com.litemall.domain.VO.OrderTimelineVO;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleReceiveResultDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.AftersaleRejectDTO;
import com.litemall.domain.dto.AftersaleRejectResultDTO;
import com.litemall.domain.dto.AftersaleSkipReceiveDTO;
import com.litemall.domain.dto.AutoCloseResultDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.BizIllegalException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.NotFoundException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.service.IAftersaleService;
import com.litemall.support.Money;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <p>
 * 售后域服务实现（R1-03 / 07 §1.2-§1.3、§4）。
 * </p>
 * <p>
 * 关键口径：
 * <ul>
 *   <li><b>按订单行售后</b>：{@code order_goods_id + number}；同一订单行同时只能存在一条未完结售后
 *       （0/1/2/3），并发靠「插入前判定该行无未完结售后」+ {@code idx_as_order_goods} 收敛。</li>
 *   <li><b>状态跃迁一律 CAS</b>（{@code eq(status, X)} 条件更新），affected=0 → 409；不允许读-判-写。</li>
 *   <li><b>退款两段式</b>（CN-07，不加全局事务）：① 本地置 {@code refunding} → ② 调 pay
 *       {@code /inner/pay/refund}（幂等键 {@code aftersaleId}）→ ③ 回补 CAS → ④ 本地置
 *       {@code completed} + 回写订单行。回补失败 → HTTP 200 + {@code restockPending=true}，
 *       售后单停在 {@code refunding} 且 {@code restock_status=2}，由 CN-08 重试，<b>绝不返回 409</b>。</li>
 *   <li><b>回补矩阵</b>：未发货仅退款（矩阵①）、已发货退货退款（矩阵③）需回补；
 *       已发货仅退款（矩阵④）不回补但 {@code restock_status} 仍置 1。</li>
 *   <li><b>35 列不变</b>：售后表没有 {@code refund_id}/{@code audit_time} 列，
 *       退款单号与时间取 {@code litemall_refund}（只读），审核时间由 {@code handle_time} 承载。</li>
 * </ul>
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AftersaleServiceImpl extends ServiceImpl<AftersaleMapper, Aftersale> implements IAftersaleService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SN_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 售后申请窗口：确认收货/完成后 7 天内（BR-AS-04） */
    private static final long WINDOW_DAYS = 7L;
    /** 待寄回截止：审核通过后 7 天（BR-AS-10） */
    private static final long RETURN_DAYS = 7L;
    /** 退货退款凭证图片 1~6 张（07 §4.1） */
    private static final int MAX_IMAGES = 6;
    /** 自动关闭单次上限（CN-IN-03） */
    private static final int AUTO_CLOSE_BATCH = 200;
    /** 售后编号并发重试次数 */
    private static final int SN_MAX_RETRY = 3;

    private static final String LOG_TARGET_TYPE = "aftersale";

    private static final String CODE_AS_NOT_FOUND = "AFTERSALE_NOT_FOUND";
    private static final String CODE_AS_STATUS_ILLEGAL = "AFTERSALE_STATUS_ILLEGAL";
    private static final String CODE_AS_EXISTS = "AFTERSALE_EXISTS";
    private static final String CODE_AS_QTY_EXCEED = "AFTERSALE_QTY_EXCEED";
    private static final String CODE_AS_WINDOW_CLOSED = "AFTERSALE_WINDOW_CLOSED";
    private static final String CODE_AS_NOT_DELIVERED = "AFTERSALE_NOT_DELIVERED";
    private static final String CODE_AS_TYPE_ILLEGAL = "AFTERSALE_TYPE_ILLEGAL";
    private static final String CODE_REFUND_AMOUNT_MISMATCH = "REFUND_AMOUNT_MISMATCH";
    private static final String CODE_ORDER_STATUS_ILLEGAL = "ORDER_STATUS_ILLEGAL";
    private static final String CODE_PARAM_INVALID = BadRequestException.PARAM_INVALID;

    /** 售后编号序号（AS + yyyyMMdd + 6 位） */
    private static final AtomicLong SN_SEQUENCE = new AtomicLong(ThreadLocalRandom.current().nextLong(1_000_000));

    private final OrderMapper orderMapper;
    private final OrderGoodsMapper orderGoodsMapper;
    private final OrderLogMapper orderLogMapper;
    private final PayClient payClient;
    private final GoodsClient goodsClient;
    private final MarketingClient marketingClient;
    private final TransactionTemplate transactionTemplate;

    /** 售后退货地址：全局配置唯一 key（Q9 ①），不落库 */
    @Value("${hm.aftersale.returnAddress:}")
    private String configuredReturnAddress;

    /* ==================================================================== */
    /* ============================== C 端 ================================ */
    /* ==================================================================== */

    @Override
    public AftersaleApplyVO apply(AftersaleApplyDTO dto) {
        Long userId = requireUser();
        OrderGoods og = requireLine(dto.getOrderGoodsId());
        Order order = requireOrder(og.getOrderId());
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new ForbiddenException("无权操作该订单商品");
        }
        if (dto.getOrderId() != null && !Objects.equals(dto.getOrderId(), order.getId())) {
            throw new BadRequestException("orderId 与订单行所属订单不一致", CODE_PARAM_INVALID);
        }
        Integer type = dto.getType();
        if (!Objects.equals(type, AftersaleStatus.TYPE_REFUND_ONLY)
                && !Objects.equals(type, AftersaleStatus.TYPE_RETURN_GOODS)) {
            throw new BadRequestException("售后类型只能是 1（仅退款）或 2（退货退款）", CODE_AS_TYPE_ILLEGAL);
        }
        assertApplyAllowed(order, type);
        if (Objects.equals(og.getRefundStatus(), 2)) {
            throw new ConflictException("该商品已全额退款，不能再次申请", CODE_AS_STATUS_ILLEGAL);
        }
        if (countUnfinishedByLine(og.getId()) > 0) {
            throw new ConflictException("该商品已有处理中的售后", CODE_AS_EXISTS);
        }
        int remain = safeInt(og.getNumber()) - safeInt(og.getRefundedNumber());
        int number = dto.getNumber() == null ? 0 : dto.getNumber();
        if (remain <= 0) {
            throw new ConflictException("该商品已无可申请件数", CODE_AS_STATUS_ILLEGAL);
        }
        if (number < 1 || number > remain) {
            throw new BadRequestException("申请数量须在 1~" + remain + " 之间", CODE_AS_QTY_EXCEED);
        }
        List<String> images = normalizeImages(dto.getImages(), type);
        String reason = requireField(dto.getReason(), "售后原因");
        if (reason.length() > 255) {
            throw new BadRequestException("售后原因不能超过 255 字", CODE_PARAM_INVALID);
        }
        Integer amount = refundAmount(og, number);
        if (dto.getAmount() != null && !dto.getAmount().equals(amount)) {
            throw new BadRequestException("退款金额与应付金额不一致", CODE_REFUND_AMOUNT_MISMATCH);
        }
        LocalDateTime now = LocalDateTime.now();
        Aftersale a = new Aftersale()
                .setAftersaleSn(generateSn())
                .setOrderId(order.getId())
                .setOrderGoodsId(og.getId())
                .setUserId(userId)
                .setType(type)
                .setNumber(number)
                // 列宽 varchar(31)：契约允许 ≤255，落库按列宽截断（自测文档「已知差异」有说明）
                .setReason(StrUtil.maxLength(reason, 31))
                .setComment(StrUtil.maxLength(StrUtil.trimToNull(dto.getComment()), 511))
                .setPictures(images.isEmpty() ? null : JSONUtil.toJsonStr(images))
                .setAmount(fenToYuan(amount))
                .setStatus(AftersaleStatus.APPLIED)
                .setRestockStatus(AftersaleStatus.RESTOCK_NONE)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        saveWithSnRetry(a);
        writeLog("aftersale:apply", a.getId(), null, AftersaleStatus.label(a.getStatus()),
                "订单行 " + og.getId() + " 申请 " + number + " 件");
        return toApplyVO(a);
    }

    @Override
    public PageVO<AftersaleListItemVO> list(String status, Integer pageNo, Integer pageSize,
                                            String sortBy, Boolean isAsc) {
        Long userId = requireUser();
        List<Integer> codes = AftersaleStatus.parseMulti(status);
        if (codes == null) {
            throw new BadRequestException("status 取值非法", CODE_PARAM_INVALID);
        }
        int pn = normalizePageNo(pageNo);
        int ps = normalizePageSize(pageSize);
        boolean asc = Boolean.TRUE.equals(isAsc);
        String sort = StrUtil.blankToDefault(StrUtil.trimToNull(sortBy), "applyTime");
        LambdaQueryWrapper<Aftersale> qw = new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getUserId, userId)
                .eq(Aftersale::getDeleted, false);
        if (!codes.isEmpty()) {
            qw.in(Aftersale::getStatus, codes);
        }
        switch (sort) {
            case "applyTime" -> qw.orderBy(true, asc, Aftersale::getAddTime);
            case "amount" -> qw.orderBy(true, asc, Aftersale::getAmount);
            default -> throw new BadRequestException("sortBy 仅支持 applyTime / amount", CODE_PARAM_INVALID);
        }
        qw.orderBy(true, asc, Aftersale::getId);
        Page<Aftersale> page = page(new Page<>(pn, ps), qw);
        return new PageVO<>(page.getTotal(), page.getPages(), toListItems(page.getRecords()));
    }

    @Override
    public AftersaleDetailVO detail(Long aftersaleId) {
        Long userId = requireUser();
        Aftersale a = requireOwnAftersale(aftersaleId, userId);
        Order order = requireOrder(a.getOrderId());
        OrderGoods og = a.getOrderGoodsId() == null ? null : orderGoodsMapper.selectById(a.getOrderGoodsId());
        AftersaleDetailVO vo = new AftersaleDetailVO();
        fillListItem(vo, a, order, og);
        vo.setReason(a.getReason());
        vo.setComment(a.getComment());
        vo.setImages(parseImages(a.getPictures()));
        vo.setReturnAddress(Objects.equals(a.getType(), AftersaleStatus.TYPE_RETURN_GOODS)
                ? StrUtil.trimToNull(configuredReturnAddress) : null);
        vo.setReturnShipCompany(a.getReturnShipCompany());
        vo.setReturnShipSn(a.getReturnShipSn());
        vo.setReturnShipTime(format(a.getReturnShipTime()));
        vo.setReceiveTime(format(a.getReceiveTime()));
        vo.setAuditTime(format(a.getHandleTime()));
        vo.setRestockPending(isRestockPending(a));
        vo.setCloseReason(a.getCloseReason());
        RefundBriefDTO refund = baseMapper.selectRefundBrief(a.getId());
        vo.setRefundTime(refund == null ? null : format(refund.getRefundTime()));
        vo.setTimeline(buildTimeline(a, refund));
        return vo;
    }

    @Override
    public void cancel(Long aftersaleId) {
        Long userId = requireUser();
        Aftersale a = requireOwnAftersale(aftersaleId, userId);
        if (!Objects.equals(a.getStatus(), AftersaleStatus.APPLIED)) {
            throw new ConflictException("已进入处理流程，不能撤销", CODE_AS_STATUS_ILLEGAL);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .eq(Aftersale::getUserId, userId)
                .eq(Aftersale::getStatus, AftersaleStatus.APPLIED)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, AftersaleStatus.CANCELLED)
                .set(Aftersale::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("已进入处理流程，不能撤销", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:cancel", a.getId(), null, AftersaleStatus.label(AftersaleStatus.CANCELLED), "用户撤销申请");
    }

    @Override
    public AftersaleReturnShipVO returnShip(AftersaleReturnShipDTO dto) {
        Long userId = requireUser();
        Aftersale a = requireOwnAftersale(dto.getAftersaleId(), userId);
        if (!Objects.equals(a.getStatus(), AftersaleStatus.WAIT_RETURN)) {
            throw new ConflictException("当前售后状态不能填写退货物流", CODE_AS_STATUS_ILLEGAL);
        }
        LocalDateTime now = LocalDateTime.now();
        if (a.getReturnDeadline() != null && a.getReturnDeadline().isBefore(now)) {
            throw new ConflictException("已超过寄回截止时间", CODE_AS_STATUS_ILLEGAL);
        }
        String company = requireField(dto.getReturnShipCompany(), "退货物流公司");
        String sn = requireField(dto.getReturnShipSn(), "退货物流单号");
        if (company.length() > 64 || sn.length() > 64) {
            throw new BadRequestException("退货物流公司与单号均不能超过 64 字", CODE_PARAM_INVALID);
        }
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .eq(Aftersale::getUserId, userId)
                .eq(Aftersale::getStatus, AftersaleStatus.WAIT_RETURN)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, AftersaleStatus.WAIT_RECEIVE)
                .set(Aftersale::getReturnShipCompany, company)
                .set(Aftersale::getReturnShipSn, sn)
                .set(Aftersale::getReturnShipTime, now)
                .set(Aftersale::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("当前售后状态不能填写退货物流", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:return-ship", a.getId(), null, AftersaleStatus.label(AftersaleStatus.WAIT_RECEIVE),
                company + " " + sn);
        AftersaleReturnShipVO vo = new AftersaleReturnShipVO();
        vo.setStatus(AftersaleStatus.ident(AftersaleStatus.WAIT_RECEIVE));
        vo.setStatusText(AftersaleStatus.label(AftersaleStatus.WAIT_RECEIVE));
        vo.setReturnShipTime(format(now));
        return vo;
    }

    /* ==================================================================== */
    /* ============================ 管理端 ================================ */
    /* ==================================================================== */

    @Override
    public PageVO<AftersaleAdminDTO> adminList(Integer pageNo, Integer pageSize, String status, Integer type,
                                               String aftersaleSn, String orderSn, String keyword,
                                               String applyTimeMin, String applyTimeMax,
                                               String sortBy, Boolean isAsc) {
        List<Integer> codes = AftersaleStatus.parseMulti(status);
        if (codes == null) {
            throw new BadRequestException("status 取值非法", CODE_PARAM_INVALID);
        }
        if (type != null && (type < AftersaleStatus.TYPE_LEGACY || type > AftersaleStatus.TYPE_RETURN_GOODS)) {
            throw new BadRequestException("type 取值非法", CODE_PARAM_INVALID);
        }
        String kw = StrUtil.trimToNull(keyword);
        if (kw != null && kw.length() > 64) {
            throw new BadRequestException("keyword 不能超过 64 字", CODE_PARAM_INVALID);
        }
        String min = format(parseTime(applyTimeMin, "applyTimeMin"));
        String max = format(parseTime(applyTimeMax, "applyTimeMax"));
        String sort = StrUtil.blankToDefault(StrUtil.trimToNull(sortBy), "applyTime");
        String orderBy = switch (sort) {
            case "applyTime" -> "a.add_time";
            case "amount" -> "a.amount";
            default -> throw new BadRequestException("sortBy 仅支持 applyTime / amount", CODE_PARAM_INVALID);
        };
        String direction = Boolean.TRUE.equals(isAsc) ? "ASC" : "DESC";
        Page<AftersaleAdminDTO> page = new Page<>(normalizePageNo(pageNo), normalizePageSize(pageSize));
        List<AftersaleAdminDTO> rows = baseMapper.selectAdminPage(page, codes, type,
                StrUtil.trimToNull(aftersaleSn), StrUtil.trimToNull(orderSn), kw, min, max, orderBy, direction);
        for (AftersaleAdminDTO row : rows) {
            decorateAdminRow(row);
        }
        return new PageVO<>(page.getTotal(), page.getPages(), rows);
    }

    @Override
    public AftersaleAdminDetailDTO adminDetail(Long aftersaleId) {
        Aftersale a = requireAftersale(aftersaleId);
        Order order = requireOrder(a.getOrderId());
        OrderGoods og = a.getOrderGoodsId() == null ? null : orderGoodsMapper.selectById(a.getOrderGoodsId());
        RefundBriefDTO refund = baseMapper.selectRefundBrief(a.getId());
        AftersaleAdminDetailDTO dto = new AftersaleAdminDetailDTO();
        dto.setAftersaleId(a.getId());
        dto.setAftersaleSn(a.getAftersaleSn());
        dto.setOrderId(a.getOrderId());
        dto.setOrderSn(order.getOrderSn());
        dto.setOrderGoodsId(a.getOrderGoodsId());
        dto.setUserId(a.getUserId());
        dto.setGoodsName(og == null ? null : og.getGoodsName());
        dto.setPicUrl(og == null ? null : og.getPicUrl());
        dto.setSpecText(og == null ? null : specText(og.getSpecifications()));
        dto.setNumber(a.getNumber());
        dto.setType(a.getType());
        dto.setTypeText(AftersaleStatus.typeLabel(a.getType()));
        dto.setStatus(AftersaleStatus.ident(a.getStatus()));
        dto.setStatusText(AftersaleStatus.label(a.getStatus()));
        dto.setAmount(Money.fen(a.getAmount()));
        dto.setReason(a.getReason());
        dto.setComment(a.getComment());
        dto.setImages(parseImages(a.getPictures()));
        dto.setReturnAddress(Objects.equals(a.getType(), AftersaleStatus.TYPE_RETURN_GOODS)
                ? StrUtil.trimToNull(configuredReturnAddress) : null);
        dto.setReturnShipCompany(a.getReturnShipCompany());
        dto.setReturnShipSn(a.getReturnShipSn());
        dto.setReturnShipTime(format(a.getReturnShipTime()));
        dto.setReturnDeadline(format(a.getReturnDeadline()));
        dto.setReceiveTime(format(a.getReceiveTime()));
        dto.setAuditTime(format(a.getHandleTime()));
        dto.setRestockStatus(a.getRestockStatus());
        dto.setRestockStatusText(AftersaleStatus.restockLabel(a.getRestockStatus()));
        dto.setRestockPending(isRestockPending(a));
        dto.setRefundTime(refund == null ? null : format(refund.getRefundTime()));
        dto.setCloseReason(a.getCloseReason());
        dto.setAuditAdmin(a.getAuditAdmin());
        List<OrderTimelineVO> nodes = buildTimeline(a, refund);
        for (OrderTimelineVO n : nodes) {
            AftersaleAdminDetailDTO.Timeline t = new AftersaleAdminDetailDTO.Timeline();
            t.setTime(n.getTime());
            t.setActor(n.getActor());
            t.setActorText(n.getActorText());
            t.setAction(n.getAction());
            dto.getTimeline().add(t);
        }
        dto.setOrder(orderBrief(order));
        dto.setLine(lineBrief(og));
        return dto;
    }

    @Override
    public AftersaleApproveResultDTO approve(AftersaleApproveDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        if (!Objects.equals(a.getStatus(), AftersaleStatus.APPLIED)) {
            throw new ConflictException("只有申请中的售后可以审核", CODE_AS_STATUS_ILLEGAL);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean returnGoods = Objects.equals(a.getType(), AftersaleStatus.TYPE_RETURN_GOODS);
        String address = null;
        LocalDateTime deadline = null;
        int target;
        if (returnGoods) {
            address = resolveReturnAddress(dto.getReturnAddress());
            deadline = now.plusDays(RETURN_DAYS);
            target = AftersaleStatus.WAIT_RETURN;
        } else {
            target = AftersaleStatus.REFUNDING;
        }
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .eq(Aftersale::getStatus, AftersaleStatus.APPLIED)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, target)
                .set(Aftersale::getAuditAdmin, dto.getAdminId())
                .set(Aftersale::getHandleTime, now)
                .set(Aftersale::getUpdateTime, now)
                .set(returnGoods, Aftersale::getReturnDeadline, deadline)
                .update();
        if (!ok) {
            throw new ConflictException("只有申请中的售后可以审核", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:approve", a.getId(), dto.getAdminId(), AftersaleStatus.label(target),
                StrUtil.maxLength(StrUtil.trimToNull(dto.getRemark()), 200));
        AftersaleApproveResultDTO result = new AftersaleApproveResultDTO();
        result.setAftersaleId(a.getId());
        result.setStatus(AftersaleStatus.ident(target));
        result.setStatusText(AftersaleStatus.label(target));
        result.setReturnAddress(address);
        result.setReturnDeadline(format(deadline));
        return result;
    }

    @Override
    public AftersaleRejectResultDTO reject(AftersaleRejectDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        String reason = requireField(dto.getReason(), "驳回原因");
        if (reason.length() > 255) {
            throw new BadRequestException("驳回原因不能超过 255 字", CODE_PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .eq(Aftersale::getStatus, AftersaleStatus.APPLIED)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, AftersaleStatus.REJECTED)
                .set(Aftersale::getCloseReason, reason)
                .set(Aftersale::getAuditAdmin, dto.getAdminId())
                .set(Aftersale::getHandleTime, now)
                .set(Aftersale::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("只有申请中的售后可以驳回", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:reject", a.getId(), dto.getAdminId(), AftersaleStatus.label(AftersaleStatus.REJECTED), reason);
        AftersaleRejectResultDTO result = new AftersaleRejectResultDTO();
        result.setAftersaleId(a.getId());
        result.setStatus(AftersaleStatus.ident(AftersaleStatus.REJECTED));
        result.setStatusText(AftersaleStatus.label(AftersaleStatus.REJECTED));
        result.setReason(reason);
        return result;
    }

    @Override
    public AftersaleReceiveResultDTO confirmReceive(AftersaleIdDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .eq(Aftersale::getStatus, AftersaleStatus.WAIT_RECEIVE)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, AftersaleStatus.REFUNDING)
                .set(Aftersale::getReceiveTime, now)
                .set(Aftersale::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("只有待收货的售后可以确认收货", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:confirm-receive", a.getId(), dto.getAdminId(),
                AftersaleStatus.label(AftersaleStatus.REFUNDING), "确认收到退货");
        return receiveResult(a.getId(), now, false);
    }

    @Override
    public AftersaleReceiveResultDTO skipReceive(AftersaleSkipReceiveDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        String reason = requireField(dto.getReason(), "跳过收货原因");
        if (reason.length() > 255) {
            throw new BadRequestException("跳过收货原因不能超过 255 字", CODE_PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .in(Aftersale::getStatus, AftersaleStatus.WAIT_RETURN, AftersaleStatus.WAIT_RECEIVE)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, AftersaleStatus.REFUNDING)
                .set(Aftersale::getReceiveTime, now)
                .set(Aftersale::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("只有待寄回或待收货的售后可以跳过收货", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:skip-receive", a.getId(), dto.getAdminId(),
                AftersaleStatus.label(AftersaleStatus.REFUNDING), reason);
        return receiveResult(a.getId(), now, true);
    }

    @Override
    public AftersaleRefundResultDTO refund(AftersaleRefundDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        // 先判状态/金额（幂等与越权闸门），无效请求不触达订单与库存
        Integer status = a.getStatus();
        if (!Objects.equals(status, AftersaleStatus.WAIT_RECEIVE)
                && !Objects.equals(status, AftersaleStatus.REFUNDING)) {
            String message = Objects.equals(status, AftersaleStatus.COMPLETED)
                    ? "该售后单已退款完成" : "当前售后状态不能发起退款";
            throw new ConflictException(message, CODE_AS_STATUS_ILLEGAL);
        }
        Integer amount = Money.fen(a.getAmount());
        if (dto.getAmount() != null && !dto.getAmount().equals(amount)) {
            throw new BadRequestException("退款金额与售后单不一致", CODE_REFUND_AMOUNT_MISMATCH);
        }
        Order order = requireOrder(a.getOrderId());
        OrderGoods og = requireLine(a.getOrderGoodsId());

        /* ① 本地置退款中（2 → 3）：CAS，连点只生效一次 */
        LocalDateTime now = LocalDateTime.now();
        if (Objects.equals(status, AftersaleStatus.WAIT_RECEIVE)) {
            boolean ok = lambdaUpdate()
                    .eq(Aftersale::getId, a.getId())
                    .eq(Aftersale::getStatus, AftersaleStatus.WAIT_RECEIVE)
                    .eq(Aftersale::getDeleted, false)
                    .set(Aftersale::getStatus, AftersaleStatus.REFUNDING)
                    .set(Aftersale::getUpdateTime, now)
                    .update();
            if (!ok) {
                throw new ConflictException("售后状态已变更，请刷新后重试", CODE_AS_STATUS_ILLEGAL);
            }
        }
        writeLog("aftersale:refund", a.getId(), dto.getAdminId(), AftersaleStatus.label(AftersaleStatus.REFUNDING),
                StrUtil.maxLength(StrUtil.trimToNull(dto.getRemark()), 200));

        /* ② 资金：pay 侧幂等键 = aftersaleId（CN-IN-02），pay 不回写 order */
        PayRefundResultDTO payResult;
        try {
            PayRefundDTO pay = new PayRefundDTO();
            pay.setAftersaleId(a.getId());
            pay.setOrderId(order.getId());
            pay.setAmount(amount);
            pay.setReason(StrUtil.blankToDefault(a.getReason(), "售后退款"));
            pay.setOperatorId(dto.getAdminId());
            payResult = payClient.refund(pay);
        } catch (Exception e) {
            log.error("[R1-03] 售后退款调用 pay 失败，售后单停在退款中待重试。aftersaleId={}", a.getId(), e);
            throw new BizIllegalException("退款资金处理失败，请稍后重试", e);
        }
        if (payResult == null || !Integer.valueOf(1).equals(payResult.getStatus())) {
            log.error("[R1-03] 售后退款未成功，售后单停在退款中待重试。aftersaleId={}, payResult={}", a.getId(), payResult);
            throw new BizIllegalException("退款资金处理未成功，请稍后重试");
        }

        /* ③ 回补：资金已退，回补失败只挂 restock_status=2，绝不把状态回滚成失败 */
        boolean restocked = tryRestock(a, order, og);
        if (!restocked) {
            writeLog("aftersale:restock-pending", a.getId(), dto.getAdminId(), "待回补", "资金已退，库存回补失败，待重试");
            return refundResult(requireAftersale(a.getId()), payResult, og, true);
        }

        /* ④ 本地完成 + 回写订单行（同一本地事务） */
        completeRefund(a, og, dto.getAdminId(), payResult);

        /* ⑤ 整单全退才退券（D5），失败不阻断已完成结果 */
        rollbackCouponIfFullyRefunded(order);

        return refundResult(requireAftersale(a.getId()), payResult,
                orderGoodsMapper.selectById(og.getId()), false);
    }

    @Override
    public AftersaleRefundResultDTO retryRestock(AftersaleIdDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        if (!Objects.equals(a.getStatus(), AftersaleStatus.REFUNDING)
                || !Objects.equals(a.getRestockStatus(), AftersaleStatus.RESTOCK_PENDING)) {
            throw new ConflictException("该售后单无需重试回补", CODE_AS_STATUS_ILLEGAL);
        }
        Order order = requireOrder(a.getOrderId());
        OrderGoods og = requireLine(a.getOrderGoodsId());
        RefundBriefDTO refund = baseMapper.selectRefundBrief(a.getId());
        if (!tryRestock(a, order, og)) {
            writeLog("aftersale:retry-restock", a.getId(), dto.getAdminId(), "待回补", "重试回补仍失败");
            return refundResult(requireAftersale(a.getId()), refund, og, true);
        }
        completeRefund(a, og, dto.getAdminId(), refund);
        rollbackCouponIfFullyRefunded(order);
        return refundResult(requireAftersale(a.getId()), refund,
                orderGoodsMapper.selectById(og.getId()), false);
    }

    @Override
    public AftersaleCloseResultDTO close(AftersaleCloseDTO dto) {
        Aftersale a = requireAftersale(dto.getAftersaleId());
        String reason = requireField(dto.getReason(), "关闭原因");
        if (reason.length() > 255) {
            throw new BadRequestException("关闭原因不能超过 255 字", CODE_PARAM_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .in(Aftersale::getStatus, AftersaleStatus.APPLIED, AftersaleStatus.WAIT_RETURN,
                        AftersaleStatus.WAIT_RECEIVE)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getStatus, AftersaleStatus.CLOSED)
                .set(Aftersale::getCloseReason, reason)
                .set(Aftersale::getAuditAdmin, dto.getAdminId())
                .set(Aftersale::getHandleTime, now)
                .set(Aftersale::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("当前售后状态不能关闭", CODE_AS_STATUS_ILLEGAL);
        }
        writeLog("aftersale:close", a.getId(), dto.getAdminId(), AftersaleStatus.label(AftersaleStatus.CLOSED), reason);
        AftersaleCloseResultDTO result = new AftersaleCloseResultDTO();
        result.setAftersaleId(a.getId());
        result.setStatus(AftersaleStatus.ident(AftersaleStatus.CLOSED));
        result.setStatusText(AftersaleStatus.label(AftersaleStatus.CLOSED));
        result.setCloseReason(reason);
        return result;
    }

    /* ==================================================================== */
    /* ============================ 系统任务 ============================== */
    /* ==================================================================== */

    @Override
    public AutoCloseResultDTO autoClose() {
        LocalDateTime now = LocalDateTime.now();
        List<Aftersale> rows = list(new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getStatus, AftersaleStatus.WAIT_RETURN)
                .isNotNull(Aftersale::getReturnDeadline)
                .le(Aftersale::getReturnDeadline, now)
                .eq(Aftersale::getDeleted, false)
                .orderByAsc(Aftersale::getReturnDeadline)
                .last("LIMIT " + AUTO_CLOSE_BATCH));
        AutoCloseResultDTO result = new AutoCloseResultDTO();
        int closed = 0;
        for (Aftersale a : rows) {
            boolean ok = lambdaUpdate()
                    .eq(Aftersale::getId, a.getId())
                    .eq(Aftersale::getStatus, AftersaleStatus.WAIT_RETURN)
                    .le(Aftersale::getReturnDeadline, now)
                    .eq(Aftersale::getDeleted, false)
                    .set(Aftersale::getStatus, AftersaleStatus.CLOSED)
                    .set(Aftersale::getCloseReason, "超时未寄回")
                    .set(Aftersale::getHandleTime, now)
                    .set(Aftersale::getUpdateTime, now)
                    .update();
            if (ok) {
                closed++;
                writeLog("aftersale:auto-close", a.getId(), null, AftersaleStatus.label(AftersaleStatus.CLOSED), "超时未寄回");
            } else {
                AutoCloseResultDTO.Item item = new AutoCloseResultDTO.Item();
                item.setId(a.getId());
                item.setReason("状态已变更");
                result.getFailed().add(item);
            }
        }
        result.setClosed(closed);
        return result;
    }

    /* ==================================================================== */
    /* ============================ 退款子步骤 ============================ */
    /* ==================================================================== */

    /**
     * 回补库存（CN-07 ③ / CN-08）：先 CAS {@code restock_status} 0/2→1，成功才调 goods release。
     * <p>
     * goods 侧不幂等，幂等由本方法 CAS 保证；release 抛异常时把状态挂回 2（仅退款中可挂），
     * 由 CN-08 重试。返回 {@code false} 表示需要「待回补」。
     * </p>
     */
    private boolean tryRestock(Aftersale a, Order order, OrderGoods og) {
        if (!needRestock(order, a)) {
            markRestock(a.getId(), AftersaleStatus.RESTOCK_DONE);
            return true;
        }
        boolean claimed = lambdaUpdate()
                .eq(Aftersale::getId, a.getId())
                .eq(Aftersale::getDeleted, false)
                .in(Aftersale::getRestockStatus, AftersaleStatus.RESTOCK_NONE, AftersaleStatus.RESTOCK_PENDING)
                .set(Aftersale::getRestockStatus, AftersaleStatus.RESTOCK_DONE)
                .set(Aftersale::getUpdateTime, LocalDateTime.now())
                .update();
        if (!claimed) {
            Aftersale fresh = baseMapper.selectById(a.getId());
            return fresh != null && Objects.equals(fresh.getRestockStatus(), AftersaleStatus.RESTOCK_DONE);
        }
        try {
            StockChangeDTO change = new StockChangeDTO();
            change.setOrderId(order.getId());
            StockChangeDTO.Item item = new StockChangeDTO.Item();
            item.setProductId(og.getProductId());
            item.setNumber(a.getNumber());
            item.setOrderGoodsId(og.getId());
            change.setItems(new ArrayList<>(List.of(item)));
            goodsClient.releaseStockBatch(change);
            return true;
        } catch (Exception e) {
            log.error("[R1-03] 售后回补库存失败，挂待回补。aftersaleId={}, productId={}", a.getId(), og.getProductId(), e);
            markPendingRestock(a.getId());
            return false;
        }
    }

    /** 回补矩阵：未发货仅退款 / 已发货退货退款需回补；已发货仅退款不回补（矩阵④） */
    private boolean needRestock(Order order, Aftersale a) {
        Integer status = order.getOrderStatus();
        boolean refundOnly = Objects.equals(a.getType(), AftersaleStatus.TYPE_REFUND_ONLY);
        if (Objects.equals(status, OrderStatus.UNDELIVERED)) {
            return refundOnly;
        }
        if (Objects.equals(status, OrderStatus.SHIPPED) || Objects.equals(status, OrderStatus.CONFIRMED)
                || Objects.equals(status, OrderStatus.COMPLETED)) {
            return !refundOnly;
        }
        return false;
    }

    private void markRestock(Long aftersaleId, int restockStatus) {
        lambdaUpdate()
                .eq(Aftersale::getId, aftersaleId)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getRestockStatus, restockStatus)
                .set(Aftersale::getUpdateTime, LocalDateTime.now())
                .update();
    }

    /** 待回补只能挂在退款中（3）上（07 §1.3） */
    private void markPendingRestock(Long aftersaleId) {
        lambdaUpdate()
                .eq(Aftersale::getId, aftersaleId)
                .eq(Aftersale::getStatus, AftersaleStatus.REFUNDING)
                .eq(Aftersale::getDeleted, false)
                .set(Aftersale::getRestockStatus, AftersaleStatus.RESTOCK_PENDING)
                .set(Aftersale::getUpdateTime, LocalDateTime.now())
                .update();
    }

    /** ④ 本地完成：售后置 4 + 回写订单行退款数量/状态（同一本地事务） */
    private void completeRefund(Aftersale a, OrderGoods og, Long adminId, Object payResult) {
        transactionTemplate.executeWithoutResult(s -> {
            LocalDateTime now = LocalDateTime.now();
            boolean ok = lambdaUpdate()
                    .eq(Aftersale::getId, a.getId())
                    .eq(Aftersale::getStatus, AftersaleStatus.REFUNDING)
                    .eq(Aftersale::getDeleted, false)
                    .set(Aftersale::getStatus, AftersaleStatus.COMPLETED)
                    .set(Aftersale::getUpdateTime, now)
                    .update();
            if (!ok) {
                throw new ConflictException("售后状态已变更，请刷新后重试", CODE_AS_STATUS_ILLEGAL);
            }
            writeBackLine(og, a.getNumber(), now);
            String refundId = payResult instanceof PayRefundResultDTO pr && pr.getRefundId() != null
                    ? String.valueOf(pr.getRefundId()) : null;
            writeLog("aftersale:refund-done", a.getId(), adminId, AftersaleStatus.label(AftersaleStatus.COMPLETED),
                    refundId == null ? null : "refundId=" + refundId);
        });
    }

    /** 订单行退款回写：refunded_number 累加、refund_status 0/1/2 */
    private void writeBackLine(OrderGoods og, Integer refundNumber, LocalDateTime now) {
        if (og == null) {
            return;
        }
        int total = safeInt(og.getNumber());
        int refunded = Math.min(total, safeInt(og.getRefundedNumber()) + safeInt(refundNumber));
        int refundStatus = refunded >= total ? 2 : (refunded > 0 ? 1 : 0);
        orderGoodsMapper.update(null, new LambdaUpdateWrapper<OrderGoods>()
                .eq(OrderGoods::getId, og.getId())
                .set(OrderGoods::getRefundedNumber, refunded)
                .set(OrderGoods::getRefundStatus, refundStatus)
                .set(OrderGoods::getUpdateTime, now));
    }

    /** D5：整单全退（所有行退款状态=2 且无未完结售后）才回退优惠券 */
    private void rollbackCouponIfFullyRefunded(Order order) {
        if (order.getCouponPrice() == null || order.getCouponPrice().signum() <= 0) {
            return;
        }
        List<OrderGoods> lines = orderGoodsMapper.selectList(new LambdaQueryWrapper<OrderGoods>()
                .eq(OrderGoods::getOrderId, order.getId())
                .eq(OrderGoods::getDeleted, false));
        boolean fullyRefunded = !lines.isEmpty()
                && lines.stream().allMatch(og -> Objects.equals(og.getRefundStatus(), 2));
        if (!fullyRefunded) {
            return;
        }
        long unfinished = countUnfinishedByOrder(order.getId());
        if (unfinished > 0) {
            return;
        }
        try {
            CouponUseDTO rollback = new CouponUseDTO();
            rollback.setOrderId(order.getId());
            marketingClient.rollbackCoupon(rollback);
        } catch (Exception e) {
            log.error("[R1-03] 整单退款回退优惠券失败，需人工核对。orderId={}", order.getId(), e);
        }
    }

    /* ==================================================================== */
    /* ============================== 工具 ================================ */
    /* ==================================================================== */

    private Long requireUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }

    private Order requireOrder(Long orderId) {
        Order order = orderId == null ? null : orderMapper.selectById(orderId);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            throw new NotFoundException("订单不存在", "ORDER_NOT_FOUND");
        }
        return order;
    }

    private OrderGoods requireLine(Long orderGoodsId) {
        OrderGoods og = orderGoodsId == null ? null : orderGoodsMapper.selectById(orderGoodsId);
        if (og == null || Boolean.TRUE.equals(og.getDeleted())) {
            throw new NotFoundException("订单商品不存在", CODE_AS_NOT_FOUND);
        }
        return og;
    }

    private Aftersale requireAftersale(Long aftersaleId) {
        Aftersale a = aftersaleId == null ? null : baseMapper.selectById(aftersaleId);
        if (a == null || Boolean.TRUE.equals(a.getDeleted())) {
            throw new NotFoundException("售后单不存在", CODE_AS_NOT_FOUND);
        }
        return a;
    }

    /** C 端：不存在与非本人统一 404，避免探测（07 §4.3） */
    private Aftersale requireOwnAftersale(Long aftersaleId, Long userId) {
        Aftersale a = requireAftersale(aftersaleId);
        if (!Objects.equals(a.getUserId(), userId)) {
            throw new NotFoundException("售后单不存在", CODE_AS_NOT_FOUND);
        }
        return a;
    }

    /** 售后申请资格矩阵（07 §4.1） */
    private void assertApplyAllowed(Order order, Integer type) {
        Integer status = order.getOrderStatus();
        if (Objects.equals(status, OrderStatus.UNPAID) || Objects.equals(status, OrderStatus.CANCELED)) {
            throw new ConflictException("当前订单状态不可申请售后", CODE_ORDER_STATUS_ILLEGAL);
        }
        if (Objects.equals(status, OrderStatus.UNDELIVERED)
                && Objects.equals(type, AftersaleStatus.TYPE_RETURN_GOODS)) {
            throw new ConflictException("订单尚未发货，不能申请退货退款", CODE_AS_NOT_DELIVERED);
        }
        if ((Objects.equals(status, OrderStatus.CONFIRMED) || Objects.equals(status, OrderStatus.COMPLETED))
                && order.getConfirmTime() != null
                && order.getConfirmTime().plusDays(WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new ConflictException("已超过售后申请期限（确认收货后 " + WINDOW_DAYS + " 天）", CODE_AS_WINDOW_CLOSED);
        }
    }

    private long countUnfinishedByLine(Long orderGoodsId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getOrderGoodsId, orderGoodsId)
                .eq(Aftersale::getDeleted, false)
                .in(Aftersale::getStatus, toIntegerList(AftersaleStatus.UNFINISHED)));
        return count == null ? 0 : count;
    }

    private long countUnfinishedByOrder(Long orderId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getOrderId, orderId)
                .eq(Aftersale::getDeleted, false)
                .in(Aftersale::getStatus, toIntegerList(AftersaleStatus.UNFINISHED)));
        return count == null ? 0 : count;
    }

    private List<Integer> toIntegerList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values) {
            list.add(v);
        }
        return list;
    }

    /** 按行实付比例计算本次退款额（分）；BR-AS-05 / BR-AS-15 */
    private Integer refundAmount(OrderGoods og, int number) {
        BigDecimal actual = og.getActualAmount() == null ? BigDecimal.ZERO : og.getActualAmount();
        int total = safeInt(og.getNumber());
        if (total <= 0) {
            return 0;
        }
        BigDecimal ratio = BigDecimal.valueOf(number)
                .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
        return Money.fen(actual.multiply(ratio).setScale(2, RoundingMode.HALF_UP));
    }

    private List<String> normalizeImages(List<String> images, Integer type) {
        List<String> list = new ArrayList<>();
        if (images != null) {
            for (String img : images) {
                String v = StrUtil.trimToNull(img);
                if (v != null) {
                    list.add(v);
                }
            }
        }
        if (list.size() > MAX_IMAGES) {
            throw new BadRequestException("凭证图片最多 " + MAX_IMAGES + " 张", CODE_PARAM_INVALID);
        }
        if (Objects.equals(type, AftersaleStatus.TYPE_RETURN_GOODS) && list.isEmpty()) {
            throw new BadRequestException("退货退款必须上传 1~" + MAX_IMAGES + " 张凭证图片", CODE_PARAM_INVALID);
        }
        return list;
    }

    private List<String> parseImages(String pictures) {
        if (StrUtil.isBlank(pictures)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSONUtil.toList(pictures, String.class));
        } catch (Exception e) {
            return new ArrayList<>(List.of(pictures));
        }
    }

    private String requireField(String value, String label) {
        String v = StrUtil.trimToNull(value);
        if (v == null) {
            throw new BadRequestException(label + "不能为空", CODE_PARAM_INVALID);
        }
        return v;
    }

    private String resolveReturnAddress(String fromRequest) {
        String address = StrUtil.trimToNull(fromRequest);
        if (address == null) {
            address = StrUtil.trimToNull(configuredReturnAddress);
        }
        if (address == null) {
            throw new BadRequestException("退货地址未配置（hm.aftersale.returnAddress）", CODE_PARAM_INVALID);
        }
        if (address.length() > 255) {
            throw new BadRequestException("退货地址不能超过 255 字", CODE_PARAM_INVALID);
        }
        return address;
    }

    private Integer safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal fenToYuan(Integer fen) {
        return fen == null ? null : BigDecimal.valueOf(fen, 2);
    }

    private boolean isRestockPending(Aftersale a) {
        return Objects.equals(a.getRestockStatus(), AftersaleStatus.RESTOCK_PENDING);
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private LocalDateTime parseTime(String value, String field) {
        String v = StrUtil.trimToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v, DT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " 时间格式应为 yyyy-MM-dd HH:mm:ss", CODE_PARAM_INVALID);
        }
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(DT);
    }

    private String specText(String specifications) {
        if (StrUtil.isBlank(specifications)) {
            return null;
        }
        try {
            List<String> list = JSONUtil.toList(specifications, String.class);
            return list.isEmpty() ? null : String.join(" / ", list);
        } catch (Exception e) {
            return specifications;
        }
    }

    private String generateSn() {
        long seq = Math.floorMod(SN_SEQUENCE.incrementAndGet(), 1_000_000L);
        return "AS" + LocalDate.now().format(SN_DATE) + String.format("%06d", seq);
    }

    /** 售后编号唯一键冲突换号重试（与订单编号同策略） */
    private void saveWithSnRetry(Aftersale a) {
        for (int i = 0; i < SN_MAX_RETRY; i++) {
            try {
                baseMapper.insert(a);
                return;
            } catch (DuplicateKeyException e) {
                a.setAftersaleSn(generateSn());
            }
        }
        baseMapper.insert(a);
    }

    /** 运营动作留痕（litemall_log，target_type=aftersale） */
    private void writeLog(String action, Long aftersaleId, Long adminId, String result, String comment) {
        LocalDateTime now = LocalDateTime.now();
        OrderLog row = new OrderLog()
                .setAction(action)
                .setTargetType(LOG_TARGET_TYPE)
                .setTargetId(aftersaleId)
                .setAdmin(adminId == null ? null : String.valueOf(adminId))
                .setStatus(1)
                .setResult(StrUtil.maxLength(result, 120))
                .setComment(StrUtil.maxLength(comment, 250))
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        try {
            orderLogMapper.insert(row);
        } catch (Exception e) {
            log.error("[R1-03] 售后操作日志写入失败, action={}, aftersaleId={}", action, aftersaleId, e);
        }
    }

    private AftersaleApplyVO toApplyVO(Aftersale a) {
        AftersaleApplyVO vo = new AftersaleApplyVO();
        vo.setAftersaleId(a.getId());
        vo.setAftersaleSn(a.getAftersaleSn());
        vo.setStatus(AftersaleStatus.ident(a.getStatus()));
        vo.setStatusText(AftersaleStatus.label(a.getStatus()));
        vo.setType(a.getType());
        vo.setTypeText(AftersaleStatus.typeLabel(a.getType()));
        vo.setAmount(Money.fen(a.getAmount()));
        vo.setNumber(a.getNumber());
        vo.setOrderGoodsId(a.getOrderGoodsId());
        vo.setOrderId(a.getOrderId());
        vo.setApplyTime(format(a.getAddTime()));
        return vo;
    }

    private List<AftersaleListItemVO> toListItems(List<Aftersale> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = rows.stream().map(Aftersale::getOrderId).filter(Objects::nonNull).distinct().toList();
        List<Long> lineIds = rows.stream().map(Aftersale::getOrderGoodsId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Order> orders = new HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Order o : orderMapper.selectBatchIds(orderIds)) {
                orders.put(o.getId(), o);
            }
        }
        Map<Long, OrderGoods> lines = new HashMap<>();
        if (!lineIds.isEmpty()) {
            for (OrderGoods og : orderGoodsMapper.selectBatchIds(lineIds)) {
                lines.put(og.getId(), og);
            }
        }
        List<AftersaleListItemVO> list = new ArrayList<>(rows.size());
        for (Aftersale a : rows) {
            AftersaleListItemVO vo = new AftersaleListItemVO();
            fillListItem(vo, a, orders.get(a.getOrderId()),
                    a.getOrderGoodsId() == null ? null : lines.get(a.getOrderGoodsId()));
            list.add(vo);
        }
        return list;
    }

    private void fillListItem(AftersaleListItemVO vo, Aftersale a, Order order, OrderGoods og) {
        vo.setAftersaleId(a.getId());
        vo.setAftersaleSn(a.getAftersaleSn());
        vo.setOrderId(a.getOrderId());
        vo.setOrderSn(order == null ? null : order.getOrderSn());
        vo.setOrderGoodsId(a.getOrderGoodsId());
        vo.setGoodsName(og == null ? null : og.getGoodsName());
        vo.setPicUrl(og == null ? null : og.getPicUrl());
        vo.setSpecText(og == null ? null : specText(og.getSpecifications()));
        vo.setNumber(a.getNumber());
        vo.setType(a.getType());
        vo.setTypeText(AftersaleStatus.typeLabel(a.getType()));
        vo.setStatus(AftersaleStatus.ident(a.getStatus()));
        vo.setStatusText(AftersaleStatus.label(a.getStatus()));
        vo.setAmount(Money.fen(a.getAmount()));
        vo.setRestockStatus(a.getRestockStatus());
        vo.setRestockStatusText(AftersaleStatus.restockLabel(a.getRestockStatus()));
        vo.setReturnShipSn(a.getReturnShipSn());
        vo.setReturnDeadline(format(a.getReturnDeadline()));
        vo.setApplyTime(format(a.getAddTime()));
    }

    private void decorateAdminRow(AftersaleAdminDTO row) {
        row.setTypeText(AftersaleStatus.typeLabel(row.getType()));
        Integer status = AftersaleStatus.parse(row.getStatus());
        // CN-01 出参 status 必须是 1.2 的字符串标识（库里是 tinyint，SQL 取回的是数字串）
        row.setStatus(AftersaleStatus.ident(status));
        row.setStatusText(AftersaleStatus.label(status));
        row.setRestockStatus(row.getRestockStatus() == null ? AftersaleStatus.RESTOCK_NONE : row.getRestockStatus());
        row.setRestockStatusText(AftersaleStatus.restockLabel(row.getRestockStatus()));
        row.setSpecText(specText(row.getSpecText()));
    }

    private AftersaleRefundResultDTO refundResult(Aftersale a, Object refund, OrderGoods og, boolean pending) {
        AftersaleRefundResultDTO vo = new AftersaleRefundResultDTO();
        vo.setAftersaleId(a.getId());
        vo.setStatus(AftersaleStatus.ident(a.getStatus()));
        vo.setStatusText(AftersaleStatus.label(a.getStatus()));
        vo.setRestockStatus(a.getRestockStatus());
        vo.setRestockStatusText(AftersaleStatus.restockLabel(a.getRestockStatus()));
        vo.setRestockPending(pending);
        vo.setRefundAmount(Money.fen(a.getAmount()));
        if (refund instanceof PayRefundResultDTO pr) {
            vo.setRefundId(pr.getRefundId());
            vo.setRefundTime(pr.getRefundTime());
        } else if (refund instanceof RefundBriefDTO rb) {
            vo.setRefundId(rb.getRefundId());
            vo.setRefundTime(format(rb.getRefundTime()));
        }
        Integer refundStatus = og == null ? 0 : safeInt(og.getRefundStatus());
        vo.setRefundStatus(refundStatus);
        vo.setRefundStatusText(AftersaleStatus.refundStatusLabel(refundStatus));
        return vo;
    }

    private AftersaleReceiveResultDTO receiveResult(Long aftersaleId, LocalDateTime receiveTime, boolean skipped) {
        AftersaleReceiveResultDTO vo = new AftersaleReceiveResultDTO();
        vo.setAftersaleId(aftersaleId);
        vo.setStatus(AftersaleStatus.ident(AftersaleStatus.REFUNDING));
        vo.setStatusText(AftersaleStatus.label(AftersaleStatus.REFUNDING));
        vo.setReceiveTime(format(receiveTime));
        vo.setSkippedReceive(skipped);
        return vo;
    }

    private AftersaleAdminDetailDTO.OrderBrief orderBrief(Order order) {
        AftersaleAdminDetailDTO.OrderBrief brief = new AftersaleAdminDetailDTO.OrderBrief();
        brief.setOrderId(order.getId());
        brief.setOrderSn(order.getOrderSn());
        brief.setStatus(OrderStatus.ident(order.getOrderStatus()));
        brief.setStatusText(OrderStatus.label(order.getOrderStatus()));
        brief.setOrderAmount(Money.fen(order.getActualPrice()));
        brief.setRefundedAmount(completedRefundAmount(order.getId()));
        List<OrderGoods> lines = orderGoodsMapper.selectList(new LambdaQueryWrapper<OrderGoods>()
                .eq(OrderGoods::getOrderId, order.getId())
                .eq(OrderGoods::getDeleted, false));
        brief.setFullyRefunded(!lines.isEmpty()
                && lines.stream().allMatch(og -> Objects.equals(og.getRefundStatus(), 2)));
        brief.setConsignee(order.getConsignee());
        brief.setMobile(maskMobile(order.getMobile()));
        brief.setAddress(order.getAddress());
        return brief;
    }

    private AftersaleAdminDetailDTO.Line lineBrief(OrderGoods og) {
        if (og == null) {
            return null;
        }
        AftersaleAdminDetailDTO.Line line = new AftersaleAdminDetailDTO.Line();
        line.setOrderGoodsId(og.getId());
        line.setGoodsName(og.getGoodsName());
        line.setSpecText(specText(og.getSpecifications()));
        line.setNumber(og.getNumber());
        line.setPrice(Money.fen(og.getPrice()));
        line.setActualAmount(Money.fen(og.getActualAmount()));
        line.setRefundedNumber(og.getRefundedNumber());
        line.setRefundStatus(og.getRefundStatus());
        return line;
    }

    /** 已退金额（分）：该订单所有「退款完成」售后单金额之和 */
    private Integer completedRefundAmount(Long orderId) {
        List<Aftersale> list = baseMapper.selectList(new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getOrderId, orderId)
                .eq(Aftersale::getStatus, AftersaleStatus.COMPLETED)
                .eq(Aftersale::getDeleted, false));
        BigDecimal sum = BigDecimal.ZERO;
        for (Aftersale a : list) {
            if (a.getAmount() != null) {
                sum = sum.add(a.getAmount());
            }
        }
        return Money.fen(sum);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /** 售后时间线（升序）：申请 → 审核/关闭 → 寄回 → 收货 → 退款完成 */
    private List<OrderTimelineVO> buildTimeline(Aftersale a, RefundBriefDTO refund) {
        List<OrderTimelineVO> list = new ArrayList<>();
        addNode(list, a.getAddTime(), "user", "用户", "提交售后申请");
        if (a.getHandleTime() != null) {
            Integer status = a.getStatus();
            String action;
            String actor = "admin";
            String actorText = "运营";
            if (Objects.equals(status, AftersaleStatus.REJECTED)) {
                action = "售后被驳回";
            } else if (Objects.equals(status, AftersaleStatus.CLOSED)) {
                action = "售后已关闭";
            } else {
                action = "审核通过";
            }
            addNode(list, a.getHandleTime(), actor, actorText, action);
        }
        addNode(list, a.getReturnShipTime(), "user", "用户", "已寄回商品");
        addNode(list, a.getReceiveTime(), "admin", "运营", "确认收到退货");
        if (refund != null) {
            addNode(list, refund.getRefundTime(), "system", "系统", "退款完成");
        }
        list.sort(Comparator.comparing(OrderTimelineVO::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private void addNode(List<OrderTimelineVO> list, LocalDateTime time, String actor, String actorText,
                         String action) {
        if (time == null) {
            return;
        }
        OrderTimelineVO node = new OrderTimelineVO();
        node.setTime(format(time));
        node.setActor(actor);
        node.setActorText(actorText);
        node.setAction(action);
        list.add(node);
    }
}