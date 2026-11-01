package com.litemall.support;

import com.litemall.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 后台会话版本号（P-1，05-R2 实施口径 §0.5 / §5.12，设计 06 §2）：
 * 键 {@code auth:ver:{adminId}}，**不过期**（与令牌寿命同语义：值一旦 +1，旧令牌永久失效）。
 * </p>
 * <p>
 * 语义：
 * </p>
 * <ul>
 *   <li>登录成功时读当前值（缺失视为 {@code 0}）并写进 JWT 的 {@code ver} 声明；</li>
 *   <li>{@code AdminAuthFilter} 每次解析令牌后比对：{@code tokenVer != current} ⇒ {@code 401 SESSION_EXPIRED}；</li>
 *   <li>仅三处 {@code INCR}：修改密码 / 重置密码（CI-04）/ 账号被禁用（CN-37，§5.12）。</li>
 * </ul>
 * <p>
 * 可用性约定（H6/H7：缓存故障不得放大为全站故障）：
 * </p>
 * <ul>
 *   <li>{@link #current(Long)} 读失败返回 {@code null}，调用方按「版本未知，降级放行」处理，
 *       绝不因为 Redis 抖动把全部管理员踢成 401；</li>
 *   <li>{@link #increase(Long)} 是写侧门禁：Redis 不可用时**必须**报 {@code 500}（CN-37 第 6 步
 *       「不得静默成功」），否则禁用账号却不失效旧会话＝安全缺口；</li>
 *   <li>{@link #increaseQuietly(Long)} 供「旧会话失效是附带效果、主流程必须成功」的场景使用（仅 WARN）。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSessionVersion {

    /** Redis key 前缀（P-1 冻结字面量） */
    public static final String KEY_PREFIX = "auth:ver:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /**
     * 当前会话版本。
     *
     * @param adminId 管理员 ID
     * @return 版本号（键不存在时为 {@code 0}）；Redis 未装配或读写异常时返回 {@code null}（调用方降级放行）
     */
    public Integer current(Long adminId) {
        if (adminId == null) {
            return null;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.warn("Redis 未装配，无法读取会话版本 auth:ver:{}（按降级放行处理）", adminId);
            return null;
        }
        try {
            String value = redis.opsForValue().get(key(adminId));
            return value == null ? 0 : Integer.valueOf(value);
        } catch (Exception e) {
            log.warn("会话版本读取失败 auth:ver:{}（按降级放行处理）：{}", adminId, e.toString());
            return null;
        }
    }

    /**
     * 会话版本 +1（旧令牌立即失效）。
     *
     * @param adminId 管理员 ID
     * @return 自增后的版本号
     * @throws CommonException Redis 不可用 / 自增失败时抛 {@code 500}「操作失败，请重试」（不得静默成功）
     */
    public int increase(Long adminId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.error("Redis 未装配，无法失效账号 {} 的旧会话", adminId);
            throw new CommonException("操作失败，请重试", 500);
        }
        try {
            Long value = redis.opsForValue().increment(key(adminId));
            if (value == null) {
                throw new CommonException("操作失败，请重试", 500);
            }
            return value.intValue();
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("会话版本自增失败 auth:ver:{}：{}", adminId, e.toString());
            throw new CommonException("操作失败，请重试", 500);
        }
    }

    /**
     * 会话版本 +1（尽力而为）：失败只 WARN，不影响主流程返回值。
     *
     * @param adminId 管理员 ID
     * @return 自增后的版本号；失败返回 {@code null}
     */
    public Integer increaseQuietly(Long adminId) {
        try {
            return increase(adminId);
        } catch (CommonException e) {
            log.warn("会话版本自增失败（已降级，旧会话可能仍然有效）auth:ver:{}：{}", adminId, e.getMessage());
            return null;
        }
    }

    /**
     * 会话版本 key：{@code auth:ver:{adminId}}。
     *
     * @param adminId 管理员 ID
     * @return Redis key
     */
    public static String key(Long adminId) {
        return KEY_PREFIX + adminId;
    }
}