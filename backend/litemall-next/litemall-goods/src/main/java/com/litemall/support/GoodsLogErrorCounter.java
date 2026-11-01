package com.litemall.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * 商品域「日志异常计数」写入器（05-R2 实施口径 §2.3.4 的「WARN + 日志计数」；交付报告裁决 2.9）。
 * </p>
 * <p>
 * 场景：评价落库后调 order 域 {@code CN-IN-08} 把订单 301→501，最多 2 次尝试；两次都失败时
 * 评价<b>不回滚</b>（否则用户白写一次评价），只 {@code WARN} + 计数，留给运维发现 D-40 的残留订单
 * （评价成功但订单仍停在 301）。
 * </p>
 * <p>
 * <b>为什么不写 {@code litemall_log}</b>：05 §1.7 红线规定该表唯一写服务是 admin，goods 跨域写表一律禁止；
 * 因此这里改走「日志异常计数」这一既有观测面——与 admin 的 {@code LogErrorCounter} 共用 Redis key
 * {@code admin:logerr:{yyyyMMdd}}（TTL 48h），工作台 {@code CI-08.logErrorCount} 读的正是它
 * （需求 §5.14「日志异常计数需展示」的落点，08 D-37）。
 * </p>
 * <p>
 * 全部操作<b>尽力而为</b>：Redis 未装配/不可用时只记本地计数 + WARN，绝不影响评价主流程。
 * </p>
 *
 * @since 2026-11-01
 */
@Slf4j
@Component
public class GoodsLogErrorCounter {

    /** Redis key 前缀（与 admin 的 {@code LogErrorCounter.KEY_PREFIX} 保持一致，冻结字面量） */
    public static final String KEY_PREFIX = "admin:logerr:";
    /** 计数保留时长（与 admin 一致：48h） */
    public static final Duration TTL = Duration.ofHours(48);
    /** 统计日时区（08 §7.5：Asia/Shanghai） */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /** 本进程内累计次数（Redis 不可用时的兜底观测面，也用于断言/诊断） */
    private final AtomicLong localFailures = new AtomicLong();

    public GoodsLogErrorCounter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    /**
     * 记录一次「两次重试均失败」的同步失败：本地计数 +1，并尽力而为地 INCR 共享的日志异常计数。
     */
    public void increase() {
        localFailures.incrementAndGet();
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            String key = key(LocalDate.now(ZONE));
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redis.expire(key, TTL);
            }
        } catch (Exception e) {
            log.warn("日志异常计数自增失败（不影响业务）：{}", e.toString());
        }
    }

    /** 本进程内累计失败次数（诊断用） */
    public long localCount() {
        return localFailures.get();
    }

    /**
     * 今日共享日志异常计数（与 admin {@code CI-08.logErrorCount} 同源）。
     *
     * @return 计数；Redis 不可用或读数异常时返回 {@code null}（承认「不知道」，不撒谎写 0）
     */
    public Long todayCount() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        try {
            String value = redis.opsForValue().get(key(LocalDate.now(ZONE)));
            return value == null ? 0L : Long.valueOf(value);
        } catch (Exception e) {
            log.warn("日志异常计数读取失败（返回 null）：{}", e.toString());
            return null;
        }
    }

    /** 当日计数 key（{@code admin:logerr:yyyyMMdd}） */
    public static String key(LocalDate day) {
        return KEY_PREFIX + day.format(DAY);
    }

    /** 归零（仅测试使用） */
    public void reset() {
        localFailures.set(0L);
    }
}