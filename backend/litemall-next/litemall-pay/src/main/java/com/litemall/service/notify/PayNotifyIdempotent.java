package com.litemall.service.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * H2：回调幂等去重。
 * <p>
 * 以微信支付单号(transaction_id)为键做 SETNX，重复通知直接返回成功（微信会重试推送）。
 * Redis 异常时抛出，回调失败由微信重试，宁可不入账也不能重复入账。
 */
@Component
@RequiredArgsConstructor
public class PayNotifyIdempotent {

    private static final String KEY_PREFIX = "pay:notify:txn:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @return true 表示首次处理；false 表示该支付单号已处理过
     */
    public boolean firstSeen(String transactionId) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + transactionId, "1", TTL);
        return Boolean.TRUE.equals(ok);
    }

    /** 处理失败时释放占位，允许微信重试 */
    public void release(String transactionId) {
        stringRedisTemplate.delete(KEY_PREFIX + transactionId);
    }
}
