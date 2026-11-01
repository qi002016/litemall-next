package com.litemall.support;

import com.litemall.exception.CommonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@code auth:ver:{adminId}} 读写语义（P-1）。
 * <p>
 * 关键约定：读侧失败必须返回 {@code null}（调用方降级放行，不能全站 401）；
 * 写侧失败必须抛 {@code 500}（CN-37 不得静默成功）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AdminSessionVersionTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AdminSessionVersion version;

    @BeforeEach
    void setUp() {
        version = new AdminSessionVersion(redisProvider);
    }

    private void redisReady() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("key 形如 auth:ver:{adminId}")
    void keyFormat() {
        assertThat(AdminSessionVersion.key(12L)).isEqualTo("auth:ver:12");
    }

    @Test
    @DisplayName("current：键存在返回值，缺失返回 0")
    void currentReads() {
        redisReady();
        when(valueOps.get("auth:ver:7")).thenReturn("3");
        assertThat(version.current(7L)).isEqualTo(3);

        when(valueOps.get("auth:ver:8")).thenReturn(null);
        assertThat(version.current(8L)).isZero();
    }

    @Test
    @DisplayName("current：adminId 为空返回 null")
    void currentNullId() {
        assertThat(version.current(null)).isNull();
        verifyNoInteractions(redisProvider);
    }

    @Test
    @DisplayName("current：Redis 未装配 / 读异常 → null（降级放行，不抛）")
    void currentDegrades() {
        when(redisProvider.getIfAvailable()).thenReturn(null);
        assertThat(version.current(7L)).isNull();

        redisReady();
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));
        assertThat(version.current(7L)).isNull();
    }

    @Test
    @DisplayName("increase：返回自增后的版本")
    void increaseReturnsValue() {
        redisReady();
        when(valueOps.increment("auth:ver:7")).thenReturn(4L);
        assertThat(version.increase(7L)).isEqualTo(4);
    }

    @Test
    @DisplayName("increase：Redis 未装配 / 异常 / 返回 null → 500「操作失败，请重试」")
    void increaseFailsLoudly() {
        when(redisProvider.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(() -> version.increase(7L))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(500);
                    assertThat(e.getMessage()).isEqualTo("操作失败，请重试");
                });

        reset(redisProvider);
        redisReady();
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> version.increase(7L)).isInstanceOf(CommonException.class);

        reset(valueOps);
        when(valueOps.increment(anyString())).thenReturn(null);
        assertThatThrownBy(() -> version.increase(7L)).isInstanceOf(CommonException.class);
    }

    @Test
    @DisplayName("increaseQuietly：失败只返回 null，不抛")
    void increaseQuietlySwallows() {
        when(redisProvider.getIfAvailable()).thenReturn(null);
        assertThat(version.increaseQuietly(7L)).isNull();
    }
}