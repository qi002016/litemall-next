package com.litemall.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GoodsLogErrorCounter} 单测（05 §2.3.4 / 裁决 2.9：评价后 501 同步失败要留计数，
 * 且与 admin 的 {@code LogErrorCounter} 共用 {@code admin:logerr:yyyyMMdd}）。
 */
class GoodsLogErrorCounterTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate template) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> opsOf(StringRedisTemplate template) {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        return ops;
    }

    @Test
    @DisplayName("首次自增（值为 1）时设置 48h TTL，key 与 admin 同源")
    @SuppressWarnings("unchecked")
    void increaseSetsTtlOnFirstIncrement() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = opsOf(template);
        when(ops.increment(any())).thenReturn(1L);
        GoodsLogErrorCounter counter = new GoodsLogErrorCounter(providerOf(template));

        counter.increase();

        String key = GoodsLogErrorCounter.key(LocalDate.now(GoodsLogErrorCounter.ZONE));
        verify(ops).increment(key);
        verify(template).expire(eq(key), eq(Duration.ofHours(48)));
        assertThat(key).startsWith("admin:logerr:");
        assertThat(counter.localCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("非首次自增（值 > 1）不重复设置 TTL")
    @SuppressWarnings("unchecked")
    void increaseSkipsTtlWhenAlreadySet() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = opsOf(template);
        when(ops.increment(any())).thenReturn(7L);
        GoodsLogErrorCounter counter = new GoodsLogErrorCounter(providerOf(template));

        counter.increase();

        verify(template, never()).expire(any(), any());
        assertThat(counter.localCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Redis 异常只 WARN：不抛出，本地计数照记（评价主流程不受影响）")
    void increaseSwallowsRedisFailure() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = opsOf(template);
        when(ops.increment(any())).thenThrow(new IllegalStateException("redis down"));
        GoodsLogErrorCounter counter = new GoodsLogErrorCounter(providerOf(template));

        assertThatCode(counter::increase).doesNotThrowAnyException();
        assertThat(counter.localCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Redis 未装配：空操作但不抛异常，本地计数仍 +1")
    void increaseWithoutRedis() {
        GoodsLogErrorCounter counter = new GoodsLogErrorCounter(providerOf(null));

        assertThatCode(counter::increase).doesNotThrowAnyException();
        assertThat(counter.localCount()).isEqualTo(1L);
        assertThat(counter.todayCount()).isNull();
    }

    @Test
    @DisplayName("todayCount：有值返回数值、无值返回 0、Redis 异常返回 null（不撒谎写 0）")
    void todayCountReadsSharedKey() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = opsOf(template);
        when(ops.get(any())).thenReturn("12");
        assertThat(new GoodsLogErrorCounter(providerOf(template)).todayCount()).isEqualTo(12L);

        when(ops.get(any())).thenReturn(null);
        assertThat(new GoodsLogErrorCounter(providerOf(template)).todayCount()).isEqualTo(0L);

        when(ops.get(any())).thenThrow(new IllegalStateException("redis down"));
        assertThat(new GoodsLogErrorCounter(providerOf(template)).todayCount()).isNull();
    }

    @Test
    @DisplayName("key 形如 admin:logerr:yyyyMMdd（与 CI-08.logErrorCount 同源）")
    void keyFormat() {
        assertThat(GoodsLogErrorCounter.key(LocalDate.of(2026, 11, 1))).isEqualTo("admin:logerr:20261101");
    }
}