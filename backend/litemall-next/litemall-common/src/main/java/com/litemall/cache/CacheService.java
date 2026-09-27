package com.litemall.cache;

import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用缓存工具：Caffeine(本地 60s) + Redis(抖动 TTL) 二级缓存。
 * - 防穿透：空值缓存（短 TTL 60s）
 * - 防雪崩：TTL 基础值 + 随机抖动 [0, jitterSeconds)
 * - 防击穿：分布式锁回源，未拿锁的线程短暂重试读缓存
 * <p>
 * M16：由 {@code CacheAutoConfiguration} 按需装配（仅 classpath 存在 Redis 时），
 * 不再用 {@code @Component} 被组件扫描强推给所有服务（网关无 Redis 依赖会启动失败）。
 */
@Slf4j
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 本地缓存：Caffeine 60s，挡热点读 */
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build();

    private static final String NULL_TAG = "::NULL::";
    private static final long NULL_TTL = 60;
    private static final long LOCK_WAIT_MS = 50;
    private static final int LOCK_RETRY = 3;
    private static final Random RANDOM = new Random();

    /**
     * 普通读：本地 -> Redis -> DB，回写。
     *
     * @param key           Redis key
     * @param type          返回类型
     * @param ttlSeconds    Redis 基础 TTL（实际 = ttl + random[0,jitter)）
     * @param jitterSeconds 雪崩抖动区间
     * @param dbLoader      DB 回源函数
     */
    public <T> T get(String key, Class<T> type, long ttlSeconds, long jitterSeconds, Supplier<T> dbLoader) {
        return doGet(key, type, ttlSeconds, jitterSeconds, dbLoader, false);
    }

    /**
     * 热点读：回源时加分布式锁防击穿。
     */
    public <T> T getWithLock(String key, Class<T> type, long ttlSeconds, long jitterSeconds, Supplier<T> dbLoader) {
        return doGet(key, type, ttlSeconds, jitterSeconds, dbLoader, true);
    }

    /** 删缓存（写 DB 后调用） */
    public void del(String key) {
        localCache.invalidate(key);
        stringRedisTemplate.delete(key);
    }

    /** List 读：loader 返回 List，元素类型 elementType */
    @SuppressWarnings("unchecked")
    public <T> java.util.List<T> getList(String key, Class<T> elementType, long ttlSeconds, long jitterSeconds,
                                        Supplier<java.util.List<T>> dbLoader) {
        // 1.本地
        String local = localCache.getIfPresent(key);
        if (local != null) {
            return decodeList(local, elementType);
        }
        // 2.Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            localCache.put(key, json);
            return decodeList(json, elementType);
        }
        // 3.回源
        java.util.List<T> data = dbLoader.get();
        String value = data == null || data.isEmpty() ? NULL_TAG : writeJson(data);
        long actualTtl = (data == null || data.isEmpty()) ? NULL_TTL : ttlSeconds + RANDOM.nextLong(jitterSeconds + 1);
        stringRedisTemplate.opsForValue().set(key, value, actualTtl, TimeUnit.SECONDS);
        localCache.put(key, value);
        return data == null ? java.util.List.of() : data;
    }

    @SuppressWarnings("unchecked")
    private <T> java.util.List<T> decodeList(String json, Class<T> elementType) {
        if (NULL_TAG.equals(json)) return java.util.List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, elementType));
        } catch (Exception e) {
            log.warn("cache list decode fail: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    /** 按前缀批量删（SCAN） */
    public void delByPrefix(String prefix) {
        localCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        var keys = stringRedisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private <T> T doGet(String key, Class<T> type, long ttlSeconds, long jitterSeconds,
                        Supplier<T> dbLoader, boolean withLock) {
        // 1.本地缓存
        String local = localCache.getIfPresent(key);
        if (local != null) {
            return decode(local, type);
        }
        // 2.Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            localCache.put(key, json);
            return decode(json, type);
        }
        // 3.回源
        if (withLock) {
            return loadWithLock(key, type, ttlSeconds, jitterSeconds, dbLoader);
        }
        return loadAndCache(key, type, ttlSeconds, jitterSeconds, dbLoader);
    }

    private <T> T loadWithLock(String key, Class<T> type, long ttl, long jitter, Supplier<T> loader) {
        String lockKey = "lock:" + key;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：拿到锁后再读一次缓存，避免锁等待期间已被其他线程回源
                String again = stringRedisTemplate.opsForValue().get(key);
                if (again != null) {
                    localCache.put(key, again);
                    return decode(again, type);
                }
                return loadAndCache(key, type, ttl, jitter, loader);
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }
        // 没拿到锁：短暂重试读缓存
        for (int i = 0; i < LOCK_RETRY; i++) {
            try { Thread.sleep(LOCK_WAIT_MS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                localCache.put(key, json);
                return decode(json, type);
            }
        }
        // 兜底：直接回源（不阻塞）
        return loadAndCache(key, type, ttl, jitter, loader);
    }

    private <T> T loadAndCache(String key, Class<T> type, long ttl, long jitter, Supplier<T> loader) {
        T data = loader.get();
        long actualTtl = data == null ? NULL_TTL : ttl + RANDOM.nextLong(jitter + 1);
        String value = data == null ? NULL_TAG : writeJson(data);
        stringRedisTemplate.opsForValue().set(key, value, actualTtl, TimeUnit.SECONDS);
        localCache.put(key, value);
        return data;
    }

    private <T> T decode(String json, Class<T> type) {
        if (NULL_TAG.equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("cache decode fail, key={}, err={}", json, e.getMessage());
            return null;
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("cache serialize fail", e);
        }
    }
}
