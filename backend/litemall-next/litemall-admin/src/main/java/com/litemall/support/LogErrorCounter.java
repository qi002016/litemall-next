package com.litemall.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * 日志写入异常计数器（P-3，05-R2 实施口径 §0.5 / 需求 §5.14 与 08 D-37）。
 * </p>
 * <p>
 * 语义：{@code litemall_log} 写入失败时 {@code INCR admin:logerr:{yyyyMMdd}}（Redis，TTL 48h），
 * CI-08 读当日值作为工作台「日志异常」计数（{@code logErrorCount}）。
 * </p>
 * <p>
 * 全部操作<b>尽力而为</b>：
 * </p>
 * <ul>
 *   <li>{@link #increase()} 失败只 WARN，绝不向业务抛出（日志不得阻断业务，§5.11）；</li>
 *   <li>{@link #todayCount()} 读失败返回 {@code null}（承认「不知道」，不撒谎写 0）；</li>
 *   <li>Redis 未装配时 {@link #increase()} 空操作、{@link #todayCount()} 返回 {@code null}，
 *       避免工作台因缓存组件缺失而 500。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogErrorCounter {

    /** Redis key 前缀（P-3 冻结字面量） */
    public static final String KEY_PREFIX = "admin:logerr:";
    /** 计数保留时长（P-3：48h，够覆盖「今日」与跨日复核） */
    public static final Duration TTL = Duration.ofHours(48);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /**
     * 记录一次日志写入失败（尽力而为，不抛异常）。
     */
    public void increase() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            String key = key(LocalDate.now(StatsPeriod.ZONE));
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redis.expire(key, TTL);
            }
        } catch (Exception e) {
            log.warn("日志异常计数自增失败（不影响业务）：{}", e.toString());
        }
    }

    /**
     * 今日日志写入失败次数。
     *
     * @return 计数；Redis 不可用或读数异常时返回 {@code null}（调用方按「暂不可用」处理）
     */
    public Long todayCount() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        try {
            String value = redis.opsForValue().get(key(LocalDate.now(StatsPeriod.ZONE)));
            return value == null ? 0L : Long.valueOf(value);
        } catch (Exception e) {
            log.warn("日志异常计数读取失败（返回 null）：{}", e.toString());
            return null;
        }
    }

    /**
     * 当日计数 key。
     *
     * @param day 统计日（{@code Asia/Shanghai}）
     * @return {@code admin:logerr:yyyyMMdd}
     */
    public static String key(LocalDate day) {
        return KEY_PREFIX + day.format(DAY);
    }
}