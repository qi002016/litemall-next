package com.litemall.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.RefundDTO;
import com.litemall.domain.PO.Refund;
import com.litemall.domain.VO.PayParamsVO;
import com.litemall.domain.VO.PayResultVO;
import com.litemall.domain.VO.RefundVO;
import com.litemall.domain.dto.OrderPayInfoDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ForbiddenException;
import com.litemall.mapper.RefundMapper;
import com.litemall.service.IPayService;
import com.litemall.service.notify.PayNotifyIdempotent;
import com.litemall.service.notify.PayNotifyVerifier;
import com.litemall.service.notify.NotifyResult;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    /** 订单状态：101 未付款；102 待发货；201 已发货；301 已收货；401 售后 */
    private static final int STATUS_UNPAID = 101;
    private static final int STATUS_UNDELIVERED = 102;
    private static final int STATUS_SHIPPED = 201;
    private static final int STATUS_CONFIRMED = 301;
    private static final int STATUS_REFUND = 401;
    private static final int STATUS_REFUND_DONE = 402;

    private final OrderClient orderClient;
    private final PayNotifyVerifier notifyVerifier;
    private final PayNotifyIdempotent notifyIdempotent;

    @Override
    public PayParamsVO wechatPay(Long orderId) {
        OrderPayInfoDTO info = requireOwnOrder(orderId);
        if (info.getOrderStatus() == null || info.getOrderStatus() != STATUS_UNPAID) {
            throw new BadRequestException("订单状态不允许支付");
        }
        // 开发环境模拟微信支付参数
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
        return vo;
    }

    @Override
    public void notifyWechat(String serial, String timestamp, String nonce, String signature, String rawBody) {
        // 1.H2：验签（生产=微信平台证书验签+AES-GCM 解密；dev=明文解析）。失败直接抛出，订单状态不被触碰
        NotifyResult result = notifyVerifier.verify(serial, timestamp, nonce, signature, rawBody);
        if (result.orderId() == null) {
            throw new BadRequestException("订单ID不能为空");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO refund(RefundDTO dto) {
        OrderPayInfoDTO info = requireOwnOrder(dto.getOrderId());
        if (info.getOrderStatus() == null
                || (info.getOrderStatus() != STATUS_UNDELIVERED && info.getOrderStatus() != STATUS_CONFIRMED)) {
            throw new BadRequestException("订单状态不允许退款");
        }
        if (info.getActualPrice() != null && dto.getAmount().compareTo(info.getActualPrice()) > 0) {
            throw new BadRequestException("退款金额不能超过实付金额");
        }
        LocalDateTime now = LocalDateTime.now();
        Refund refund = new Refund()
                .setOrderId(dto.getOrderId())
                .setUserId(info.getUserId())
                .setAmount(dto.getAmount())
                .setReason(dto.getReason())
                .setStatus(0)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(refund);
        orderClient.markRefunded(dto.getOrderId(), dto.getAmount());
        RefundVO vo = new RefundVO();
        vo.setRefundId(refund.getId());
        vo.setStatus("processing");
        return vo;
    }

    @Override
    public PayResultVO queryResult(Long orderId) {
        OrderPayInfoDTO info = requireOwnOrder(orderId);
        PayResultVO vo = new PayResultVO();
        vo.setOrderId(orderId);
        vo.setStatus(mapStatus(info.getOrderStatus()));
        vo.setPayTime(info.getPayTime() == null ? null : info.getPayTime().format(DT));
        vo.setPayId(info.getPayId());
        return vo;
    }

    /**
     * 校验订单存在且属于当前登录用户
     */
    private OrderPayInfoDTO requireOwnOrder(Long orderId) {
        if (orderId == null) {
            throw new BadRequestException("订单ID不能为空");
        }
        Long userId = UserContext.getUser();
        OrderPayInfoDTO info = orderClient.getPayInfo(orderId);
        if (info == null) {
            throw new BadRequestException("订单不存在");
        }
        if (userId != null && !userId.equals(info.getUserId())) {
            throw new ForbiddenException("无权操作该订单");
        }
        return info;
    }

    /**
     * 订单状态 → 支付状态：unpaid / paid / refunded
     */
    private String mapStatus(Integer orderStatus) {
        if (orderStatus == null || orderStatus == STATUS_UNPAID) {
            return "unpaid";
        }
        if (orderStatus == STATUS_REFUND || orderStatus == STATUS_REFUND_DONE) {
            return "refunded";
        }
        // 102 待发货 / 201 已发货 / 301 已收货 均视为已支付
        if (orderStatus == STATUS_UNDELIVERED || orderStatus == STATUS_SHIPPED || orderStatus == STATUS_CONFIRMED) {
            return "paid";
        }
        return "paid";
    }
}
