package com.litemall.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.RefundDTO;
import com.litemall.domain.PO.Refund;
import com.litemall.domain.VO.PayParamsVO;
import com.litemall.domain.VO.PayResultVO;
import com.litemall.domain.VO.RefundVO;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.PayRefundDTO;
import com.litemall.domain.dto.PayRefundResultDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.exception.ForbiddenException;
import com.litemall.mapper.RefundMapper;
import com.litemall.service.IPayService;
import com.litemall.service.notify.NotifyResult;
import com.litemall.service.notify.PayNotifyIdempotent;
import com.litemall.service.notify.PayNotifyVerifier;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * <p>
 * 支付服务 实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl extends ServiceImpl<RefundMapper, Refund> implements IPayService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 订单状态：101 未付款；102 待发货；201 已发货；301 已收货；401 售后；402 已退款；501 已完成；103 已关闭 */
    private static final int STATUS_UNPAID = 101;
    private static final int STATUS_UNDELIVERED = 102;
    private static final int STATUS_SHIPPED = 201;
    private static final int STATUS_CONFIRMED = 301;
    private static final int STATUS_REFUND = 401;
    private static final int STATUS_REFUND_DONE = 402;
    private static final int STATUS_COMPLETED = 501;
    private static final int STATUS_CANCELED = 103;

    /** 退款单状态：0 处理中；1 退款成功；2 退款失败（R1 资金侧直接置成功，无异步通道） */
    private static final int REFUND_STATUS_SUCCESS = 1;

    /** 演示支付结果取值 */
    private static final String PAY_MODE_SUCCESS = "success";
    private static final String PAY_MODE_FAIL = "fail";

    private final OrderClient orderClient;
    private final PayNotifyVerifier notifyVerifier;
    private final PayNotifyIdempotent notifyIdempotent;

    @Override
    public PayParamsVO wechatPay(Long orderId) {
        return wechatPay(orderId, null);
    }

    @Override
    public PayParamsVO wechatPay(Long orderId, String mode) {
        OrderPayInfoDTO info = requireOwnOrder(orderId);
        String payResult = normalizeMode(mode);
        if (info.getOrderStatus() == null || info.getOrderStatus() != STATUS_UNPAID) {
            // 409：ORDER_ALREADY_PAID / ORDER_PAYABLE（ORDER_EXPIRED 需 order 侧透出 expire_time，
            //  见 03 卡:696 与 R1-02 的 OrderPayInfoDTO 改造，字段到位后在此补判定）
            if (info.getPayTime() != null) {
                throw new ConflictException("该订单已支付", "ORDER_ALREADY_PAID");
            }
            throw new ConflictException("该订单无需支付", "ORDER_NOT_PAYABLE");
        }
        // 开发环境模拟微信支付参数；mode=fail 只影响返回的 payResult，不改变订单状态、
        // 不释放库存、不退券（真正置已支付在 CI-97 回调）
        String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr = RandomUtil.randomString(32);
        String prepayId = "wx" + orderId + RandomUtil.randomNumbers(8);
        String packageValue = "prepay_id=" + prepayId;
        String signType = "RSA";
        String paySign = DigestUtil.md5Hex(orderId + nonceStr + timeStamp + "LITEMALL_MOCK");
        PayParamsVO vo = new PayParamsVO();
        vo.setTimeStamp(timeStamp);
        vo.setNonceStr(nonceStr);
        vo.setPackage_(packageValue);
        vo.setSignType(signType);
        vo.setPaySign(paySign);
        vo.setDemo(true);
        vo.setPayResult(payResult);
        return vo;
    }

    @Override
    public void notifyWechat(String serial, String timestamp, String nonce, String signature, String rawBody) {
        // 1.H2：验签（生产=微信平台证书验签+AES-GCM 解密；dev=明文解析）。失败直接抛出，订单状态不被触碰
        NotifyResult result = notifyVerifier.verify(serial, timestamp, nonce, signature, rawBody);
        if (result.orderId() == null) {
            throw new BadRequestException("订单ID不能为空", BadRequestException.PARAM_INVALID);
        }
        // 2.归一化支付单号：真实回调取 transaction_id；dev 明文缺省时用可复现的 mock 单号
        String payId = (result.payId() == null || result.payId().isBlank())
                ? "wxmock" + result.orderId()
                : result.payId();
        // 3.幂等去重：同一支付单号只入账一次；重复通知直接返回成功（微信会重试推送）
        if (!notifyIdempotent.firstSeen(payId)) {
            log.info("pay_notify_duplicate payId={}", payId);
            return;
        }
        // 4.金额校验：验签通过的实付金额必须与订单应付一致（dev 明文回调无金额字段则跳过）
        checkAmount(result);
        // 5.通知订单服务入账；失败释放幂等占位，允许微信重试
        try {
            PaySuccessDTO dto = new PaySuccessDTO();
            dto.setOrderId(result.orderId());
            dto.setPayId(payId);
            orderClient.markPaid(dto);
        } catch (Exception e) {
            notifyIdempotent.release(payId);
            throw e;
        }
    }

    /**
     * H2：校验回调金额与订单实付金额一致（分为单位）。
     */
    private void checkAmount(NotifyResult result) {
        if (result.totalFeeCents() == null) {
            return;
        }
        OrderPayInfoDTO info = orderClient.getPayInfo(result.orderId());
        if (info == null) {
            throw new BadRequestException("订单不存在");
        }
        if (info.getActualPrice() == null
                || info.getActualPrice().movePointRight(2).intValue() != result.totalFeeCents()) {
            log.error("pay_notify_amount_mismatch orderId={} expect={} actual={}",
                    result.orderId(), info.getActualPrice(), result.totalFeeCents());
            throw new BadRequestException("回调金额与订单金额不一致");
        }
    }

    /**
     * CI-105：演示直付单退款。
     * <p>
     * 语义已按 07 §5.3 收窄为「仅演示直付单可用」：R1 售后退款一律走 CN-07 → CN-IN-02，不经过本方法。
     * 资金记录与 CN-IN-02 同段逻辑（CX-06 ①）：直接落 status=1 + refund_time，**不再回调 order**
     * （OrderClient.markRefunded 已停用，CX-06 退役）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO refund(RefundDTO dto) {
        OrderPayInfoDTO info = requireOwnOrder(dto.getOrderId());
        Integer orderStatus = info.getOrderStatus();
        if (orderStatus == null) {
            throw new ConflictException("订单状态不允许退款", "ORDER_STATUS_ILLEGAL");
        }
        if (orderStatus == STATUS_REFUND || orderStatus == STATUS_REFUND_DONE) {
            // 409 ORDER_STATUS_ILLEGAL：已进入退款流程/已退款 → 引导走售后链路
            throw new ConflictException("该订单已进入退款流程，请通过售后台处理", "ORDER_STATUS_ILLEGAL");
        }
        if (orderStatus == STATUS_UNPAID) {
            throw new ConflictException("订单未支付，不允许退款", "ORDER_STATUS_ILLEGAL");
        }
        if (orderStatus != STATUS_UNDELIVERED && orderStatus != STATUS_SHIPPED && orderStatus != STATUS_CONFIRMED) {
            throw new ConflictException("订单状态不允许退款", "ORDER_STATUS_ILLEGAL");
        }
        int amountCents = dto.getAmount() == null ? 0 : dto.getAmount();
        if (amountCents <= 0) {
            throw new BadRequestException("退款金额必须大于0", BadRequestException.PARAM_INVALID);
        }
        if (amountCents > actualPriceCents(info)) {
            throw new BadRequestException("退款金额不能超过实付金额", BadRequestException.PARAM_INVALID);
        }
        // 幂等：同一订单只允许一笔成功退款（重复退按「已退款」拒绝）
        if (findSucceededRefund(dto.getOrderId()) != null) {
            throw new ConflictException("该订单已进入退款流程，请通过售后台处理", "ORDER_STATUS_ILLEGAL");
        }

        LocalDateTime now = LocalDateTime.now();
        Refund refund = new Refund()
                .setOrderId(dto.getOrderId())
                .setUserId(info.getUserId())
                .setAmount(centsToYuan(amountCents))
                .setReason(dto.getReason())
                .setStatus(REFUND_STATUS_SUCCESS)
                .setRefundTime(now)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(refund);
        RefundVO vo = new RefundVO();
        vo.setRefundId(refund.getId());
        vo.setStatus("processing");
        return vo;
    }

    /**
     * CN-IN-02：售后退款（order 服务内部调用）。幂等键 aftersaleId。
     * <p>
     * /inner/** 走裸类型 + 非 200 HTTP，因此这里不做 Result 包装；不回调 order（DR-17）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayRefundResultDTO refundFunds(PayRefundDTO dto) {
        if (dto == null || dto.getAftersaleId() == null) {
            throw new BadRequestException("售后单ID不能为空", BadRequestException.PARAM_INVALID);
        }
        if (dto.getOrderId() == null) {
            throw new BadRequestException("订单ID不能为空", BadRequestException.PARAM_INVALID);
        }
        if (dto.getAmount() == null || dto.getAmount() <= 0) {
            throw new BadRequestException("退款金额必须大于0", BadRequestException.PARAM_INVALID);
        }
        if (dto.getReason() != null && dto.getReason().length() > 255) {
            throw new BadRequestException("退款原因不能超过255个字符", BadRequestException.PARAM_INVALID);
        }
        OrderPayInfoDTO info = orderClient.getPayInfo(dto.getOrderId());
        if (info == null) {
            // /inner/** 裸类型：404 由公共层映射（R1-02 提供 NotFoundException，此处用 CommonException 表达）
            throw new NotFoundException("订单不存在", NotFoundException.RESOURCE_NOT_FOUND);
        }
        int amountCents = dto.getAmount();
        if (amountCents > actualPriceCents(info)) {
            throw new BadRequestException("退款金额不能超过实付金额", BadRequestException.PARAM_INVALID);
        }

        // 幂等：同 aftersaleId 已有成功退款 → 直接返回同一笔（并发只写 1 行，idx_refund_aftersale）
        List<Refund> existed = list(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getAftersaleId, dto.getAftersaleId())
                .eq(Refund::getDeleted, false));
        Refund succeeded = null;
        Refund pending = null;
        if (existed != null) {
            for (Refund item : existed) {
                if (item.getStatus() != null && item.getStatus() == REFUND_STATUS_SUCCESS) {
                    succeeded = item;
                    break;
                }
                if (pending == null) {
                    pending = item;
                }
            }
        }
        if (succeeded != null) {
            log.info("refund_idempotent_hit aftersaleId={} refundId={}", dto.getAftersaleId(), succeeded.getId());
            return toRefundResult(succeeded);
        }
        LocalDateTime now = LocalDateTime.now();
        if (pending != null) {
            if (toCents(pending.getAmount()) != amountCents) {
                // 同键不同额：幂等冲突（409 IDEMPOTENT_CONFLICT）
                throw new ConflictException("同一售后单的退款金额不一致", "IDEMPOTENT_CONFLICT");
            }
            pending.setStatus(REFUND_STATUS_SUCCESS)
                    .setRefundTime(now)
                    .setUpdateTime(now);
            updateById(pending);
            return toRefundResult(pending);
        }

        Refund refund = new Refund()
                .setAftersaleId(dto.getAftersaleId())
                .setOrderId(dto.getOrderId())
                .setUserId(info.getUserId())
                .setAmount(centsToYuan(amountCents))
                .setReason(dto.getReason() == null || dto.getReason().isBlank() ? "售后退款" : dto.getReason())
                .setStatus(REFUND_STATUS_SUCCESS)
                .setRefundTime(now)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(refund);
        log.info("refund_success aftersaleId={} orderId={} amountCents={} operatorId={} refundId={}",
                dto.getAftersaleId(), dto.getOrderId(), amountCents, dto.getOperatorId(), refund.getId());
        return toRefundResult(refund);
    }

    @Override
    public PayResultVO queryResult(Long orderId) {
        OrderPayInfoDTO info = requireOwnOrder(orderId);
        PayResultVO vo = new PayResultVO();
        vo.setOrderId(orderId);
        String status = mapStatus(info.getOrderStatus());
        vo.setStatus(status);
        vo.setPayTime(info.getPayTime() == null ? null : info.getPayTime().format(DT));
        vo.setPayId(info.getPayId());
        vo.setResult(mapResult(status));
        vo.setOrderStatus(info.getOrderStatus());
        vo.setOrderStatusText(mapStatusText(info.getOrderStatus()));
        // TODO(R1-02)：OrderPayInfoDTO 缺 expire_time，支付截止时间暂不可得；待订单侧透出后接线
        vo.setExpireTime(null);
        return vo;
    }

    /**
     * 校验订单存在且属于当前登录用户
     */
    private OrderPayInfoDTO requireOwnOrder(Long orderId) {
        if (orderId == null) {
            throw new BadRequestException("订单ID不能为空", BadRequestException.PARAM_INVALID);
        }
        Long userId = UserContext.getUser();
        OrderPayInfoDTO info = orderClient.getPayInfo(orderId);
        if (info == null) {
            // 契约 §4.4：CI-103/CI-105/CI-106 订单不存在 → 404 ORDER_NOT_FOUND
            throw new NotFoundException("订单不存在", "ORDER_NOT_FOUND");
        }
        if (userId != null && !userId.equals(info.getUserId())) {
            throw new ForbiddenException("无权操作该订单");
        }
        return info;
    }

    /**
     * 演示支付结果归一：缺省 success；非枚举值 → 400 PARAM_INVALID
     */
    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return PAY_MODE_SUCCESS;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!PAY_MODE_SUCCESS.equals(normalized) && !PAY_MODE_FAIL.equals(normalized)) {
            throw new BadRequestException("支付方式不合法", BadRequestException.PARAM_INVALID);
        }
        return normalized;
    }

    /**
     * 查同一订单已成功的退款行（CI-105 幂等判定）
     */
    private Refund findSucceededRefund(Long orderId) {
        List<Refund> refunds = list(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getOrderId, orderId)
                .eq(Refund::getStatus, REFUND_STATUS_SUCCESS)
                .eq(Refund::getDeleted, false));
        return refunds == null || refunds.isEmpty() ? null : refunds.get(0);
    }

    private PayRefundResultDTO toRefundResult(Refund refund) {
        PayRefundResultDTO result = new PayRefundResultDTO();
        result.setRefundId(refund.getId());
        result.setStatus(REFUND_STATUS_SUCCESS);
        result.setStatusText("退款成功");
        LocalDateTime refundTime = refund.getRefundTime() == null ? LocalDateTime.now() : refund.getRefundTime();
        result.setRefundTime(refundTime.format(DT));
        return result;
    }

    /**
     * 元 → 分（仅接口边界使用，库内仍为 DECIMAL 元）
     */
    private int toCents(BigDecimal yuan) {
        if (yuan == null) {
            return 0;
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 分 → 元（库内落 DECIMAL 元，06 §4：转换只在 DTO 边界）
     */
    private BigDecimal centsToYuan(int cents) {
        return BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 订单实付金额（分）；实付为空按 0 处理
     */
    private int actualPriceCents(OrderPayInfoDTO info) {
        if (info == null || info.getActualPrice() == null) {
            return 0;
        }
        return info.getActualPrice().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 订单状态 → 支付状态：unpaid / paid / refunded
     */
    private String mapStatus(Integer orderStatus) {
        if (orderStatus == null || orderStatus == STATUS_UNPAID || orderStatus == STATUS_CANCELED) {
            // 103 已关闭：未支付即关闭，仍归 unpaid（原实现落到默认 "paid" 是缺陷）
            return "unpaid";
        }
        if (orderStatus == STATUS_REFUND || orderStatus == STATUS_REFUND_DONE) {
            return "refunded";
        }
        // 102 待发货 / 201 已发货 / 301 已收货 / 501 已完成 均视为已支付
        return "paid";
    }

    /**
     * 支付状态 → CI-106 result：success / fail / pending
     * <p>
     * R1 不落"支付失败"状态（演示 fail 不改变订单状态），因此只有退款的终态归 fail。
     * </p>
     */
    private String mapResult(String status) {
        if ("paid".equals(status)) {
            return "success";
        }
        if ("refunded".equals(status)) {
            return "fail";
        }
        return "pending";
    }

    /**
     * 订单主状态文案（07 §1.1）
     */
    private String mapStatusText(Integer orderStatus) {
        if (orderStatus == null) {
            return null;
        }
        return switch (orderStatus) {
            case STATUS_UNPAID -> "未付款";
            case STATUS_UNDELIVERED -> "待发货";
            case STATUS_SHIPPED -> "已发货";
            case STATUS_CONFIRMED -> "已收货";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_CANCELED -> "已关闭";
            case STATUS_REFUND -> "退款中";
            case STATUS_REFUND_DONE -> "已退款";
            default -> null;
        };
    }
}