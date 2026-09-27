package com.litemall.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短信验证码工具类
 * <p>
 * 由老项目 {@code CaptchaCodeManager} 改造而来:
 * <ul>
 *   <li>存储介质:内存 ConcurrentHashMap → Redis(微服务多实例部署下,内存方案各实例不共享)</li>
 *   <li>职责扩展:从"只存取验证码"扩展为"生成 + 保存 + 校验 + 失效"完整闭环</li>
 *   <li>发送限流:沿用老项目 60 秒内不允许重复发送的策略</li>
 * </ul>
 *
 * <p>Redis Key 设计:{@code captcha:{phone}} → 验证码明文,TTL 5 分钟。
 */
@Component
@RequiredArgsConstructor
public class CaptchaCodeUtil {

    private static final String KEY_PREFIX = "captcha:";

    /** 验证码有效期:5 分钟(老项目写死 1 分钟,实际短信验证码常用 5 分钟) */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    /** 重发间隔:60 秒(沿用老项目策略,防止短信轰炸) */
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    /** 验证码长度 */
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成 6 位纯数字验证码。
     *
     * @return 随机数字字符串,长度 6,首位不为 0
     */
    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        // 首位 1-9
        sb.append(ThreadLocalRandom.current().nextInt(1, 10));
        // 其余 5 位 0-9
        for (int i = 1; i < CODE_LENGTH; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(0, 10));
        }
        return sb.toString();
    }

    /**
     * 保存验证码到 Redis。
     * <p>
     * 若同一手机号在 RESEND_INTERVAL 内已生成过验证码且未过期,返回 false,不覆盖旧码
     * (与老项目 addToCache 行为一致)。
     *
     * @param phone 手机号
     * @param code  验证码明文
     * @return true=保存成功可发送;false=重发间隔未到,拒绝发送
     */
    public boolean save(String phone, String code) {
        String key = buildKey(phone);
        // 还在重发冷却期内,不覆盖
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(hasKey)) {
            Long ttl = stringRedisTemplate.getExpire(key);
            // 剩余 TTL 大于重发间隔,说明刚发过不久
            if (ttl != null && ttl > CODE_TTL.minus(RESEND_INTERVAL).getSeconds()) {
                return false;
            }
        }
        stringRedisTemplate.opsForValue().set(key, code, CODE_TTL);
        return true;
    }

    /**
     * 校验验证码。校验通过后立即删除(防止重放)。
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return true=校验通过;false=验证码不存在/已过期/不匹配
     */
    public boolean validate(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }
        String key = buildKey(phone);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached == null) {
            return false;
        }
        boolean matched = cached.equals(code);
        if (matched) {
            stringRedisTemplate.delete(key);
        }
        return matched;
    }

    /**
     * 主动使验证码失效(如用户退出登录、注销账号)。
     *
     * @param phone 手机号
     */
    public void invalidate(String phone) {
        stringRedisTemplate.delete(buildKey(phone));
    }

    private String buildKey(String phone) {
        return KEY_PREFIX + phone;
    }
}
