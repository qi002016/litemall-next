package com.litemall.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1：CacheService 的 H7 / M7 / M8 单元测试（纯 Mockito，不连真实 Redis）。
 */
class CacheServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private CacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        cacheService = new CacheService(redis, new ObjectMapper());
    }

    @Test
    @DisplayName("M7：解码失败必须抛出并删除损坏 key（不再静默当作空值）")
    void decodeFailure_throwsAndEvicts() {
        when(valueOps.get("goods:detail:x")).thenReturn("{\"name\": broken-json");

        assertThatThrownBy(() -> cacheService.get("goods:detail:x", Object.class, 60, 0, () -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缓存数据损坏");

        verify(redis).delete("goods:detail:x");
    }

    @Test
    @DisplayName("M7：空值缓存仍返回 null（与解码故障区分）")
    void nullTag_stillReturnsNull() {
        when(valueOps.get("goods:detail:none")).thenReturn("::NULL::");

        assertThat(cacheService.get("goods:detail:none", Object.class, 60, 0, () -> null)).isNull();

        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("H7：delByPrefix 使用 SCAN 游标 + 批量删除，并广播前缀失效")
    void delByPrefix_usesScanAndBatchDelete() {
        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("goods:list:1", "goods:list:2");
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);

        cacheService.delByPrefix("goods:list:");

        verify(redis).scan(any(ScanOptions.class));
        verify(redis).delete(argThat((Collection<String> keys) ->
                keys.size() == 2 && keys.contains("goods:list:1") && keys.contains("goods:list:2")));
        verify(redis).convertAndSend(eq(CacheService.INVALIDATE_CHANNEL), eq("p:goods:list:"));
    }

    @Test
    @DisplayName("M8：del 会广播单 key 失效，供其他节点清理本地 Caffeine")
    void del_broadcastsInvalidation() {
        cacheService.del("goods:stock:9");

        verify(redis).delete("goods:stock:9");
        verify(redis).convertAndSend(eq(CacheService.INVALIDATE_CHANNEL), eq("k:goods:stock:9"));
    }
}