package com.litemall.constant;

/**
 * <p>
 * Redis key 常量类：key 统一小写、冒号分隔，禁止在业务代码中硬编码
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 下单幂等 token：order:idem:{uuid}，TTL 5 分钟 */
    public static final String ORDER_IDEM = "order:idem:";

    /** 下单幂等 token TTL（秒） */
    public static final long ORDER_IDEM_TTL_SECONDS = 300L;
}
