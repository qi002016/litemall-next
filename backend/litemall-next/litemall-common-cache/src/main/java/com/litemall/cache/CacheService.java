package com.litemall.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
 * <p>
 * P1 改动：
 * <ul>
 *   <li><b>H7</b>：{@link #delByPrefix(String)} 改用 {@code SCAN} + 批量删除，不再使用阻塞 Redis 的 {@code KEYS}</li>
 *   <li><b>M7</b>：反序列化失败不再静默返回 null（与"空值缓存"语义混同），
 *       而是删除损坏 key、{@code log.error} 并抛出，使故障可见（下次请求自动回源）</li>
 *   <li><b>M8</b>：删除/失效时向 {@link #INVALIDATE_CHANNEL} 广播，其他实例同步清理本地 Caffeine，
 *       解决多节点下本地缓存最长 60s 不一致</li>
 *   <li>新增 {@link #getShared}：跳过本地 Caffeine，只走 Redis，用于要求"失效即时全局可见"的数据（如库存）</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class CacheService {

    /** M8：本地缓存失效广播频道 */
    public static final String INVALIDATE_CHANNEL = "litemall:cache:invalidate";
    /** 广播消息前缀：单 key 失效 */
    public static final String MSG_KEY = "k:";
    /** 广播消息前缀：前缀失效 */
    public static final String MSG_PREFIX = "p:";
    /** H7：单次前缀删除的 SCAN 上限，避免误删海量 key 时长时间占用连接 */
    private static final long MAX_SCAN_KEYS = 10_000L;
    private static final long SCAN_COUNT = 500L;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 本地缓存：Caffeine 60s，挡热点读（跨节点失效由广播通道保证） */
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

    /**
     * 共享读：**跳过本地 Caffeine**，只走 Redis -> DB。
     * <p>
     * 用于"一旦失效就必须全局立刻可见"的数据（如库存）。本地缓存会让其他节点最长 60s 看到旧值，
     * 即使失效广播也存在极短竞态；这类数据直接从 Redis 读，配合 {@link #del(String)} 的全局删除即可精确失效。
     */
    public <T> T getShared(String key, Class<T> type, long ttlSeconds, long jitterSeconds, Supplier<T> dbLoader) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            return decode(key, json, type);
        }
        return loadAndCache(key, type, ttlSeconds, jitterSeconds, dbLoader).data();
    }

    /** 删缓存（写 DB 后调用）：本机失效 + Redis 删除 + 广播其他节点失效 */
    public void del(String key) {
        localCache.invalidate(key);
        stringRedisTemplate.delete(key);
        publish(MSG_KEY + key);
    }

    /** List 读：loader 返回 List，元素类型 elementType */
    public <T> List<T> getList(String key, Class<T> elementType, long ttlSeconds, long jitterSeconds,
                               Supplier<List<T>> dbLoader) {
        // 1.本地
        String local = localCache.getIfPresent(key);
        if (local != null) {
            return decodeList(key, local, elementType);
        }
        // 2.Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            localCache.put(key, json);
            return decodeList(key, json, elementType);
        }
        // 3.回源（空列表按"空值缓存"处理，沿用原有防穿透语义）
        List<T> data = dbLoader.get();
        boolean empty = data == null || data.isEmpty();
        String value = empty ? NULL_TAG : writeJson(data);
        long actualTtl = empty ? NULL_TTL : ttlSeconds + RANDOM.nextLong(jitterSeconds + 1);
        stringRedisTemplate.opsForValue().set(key, value, actualTtl, TimeUnit.SECONDS);
        localCache.put(key, value);
        return data == null ? List.of() : data;
    }

    /**
     * H7：按前缀批量删（{@code SCAN} 游标遍历 + 批量删除，不再使用 {@code KEYS}）。
     * <p>
     * 单次最多处理 {@link #MAX_SCAN_KEYS} 个 key（超出会告警），避免误删海量 key 时长时间占用连接；
     * 未删完的部分需由调用方按需再次调用。
     */
    public void delByPrefix(String prefix) {
        evictLocalByPrefix(prefix);
        Set<String> keys = scanKeys(prefix + "*");
        if (!keys.isEmpty()) {
            // delete(Collection) 内部以 pipeline 批量发送，避免逐 key 往返
            stringRedisTemplate.delete(keys);
        }
        publish(MSG_PREFIX + prefix);
    }

    /** M8：仅清理本机 Caffeine 的单 key（供广播监听器调用） */
    public void evictLocal(String key) {
        localCache.invalidate(key);
    }

    /** M8：仅清理本机 Caffeine 的前缀 key（供广播监听器调用） */
    public void evictLocalByPrefix(String prefix) {
        localCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
                if (keys.size() >= MAX_SCAN_KEYS) {
                    log.warn("[H7] delByPrefix 达到单次上限 {}，pattern={}，剩余 key 需再次调用清理",
                            MAX_SCAN_KEYS, pattern);
                    break;
                }
            }
        }
        return keys;
    }

    private void publish(String message) {
        try {
            stringRedisTemplate.convertAndSend(INVALIDATE_CHANNEL, message);
        } catch (Exception e) {
            // 广播失败不影响本机与 Redis 的删除结果：其他节点最多等到本地 TTL 到期
            log.warn("[M8] 缓存失效广播失败, message={}, err={}", message, e.getMessage());
        }
    }

    private <T> T doGet(String key, Class<T> type, long ttlSeconds, long jitterSeconds,
                        Supplier<T> dbLoader, boolean withLock) {
        // 1.本地缓存
        String local = localCache.getIfPresent(key);
        if (local != null) {
            return decode(key, local, type);
        }
        // 2.Redis
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json != null) {
            localCache.put(key, json);
            return decode(key, json, type);
        }
        // 3.回源
        if (withLock) {
            return loadWithLock(key, type, ttlSeconds, jitterSeconds, dbLoader);
        }
        Encoded<T> encoded = loadAndCache(key, type, ttlSeconds, jitterSeconds, dbLoader);
        localCache.put(key, encoded.json());
        return encoded.data();
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
                    return decode(key, again, type);
                }
                Encoded<T> encoded = loadAndCache(key, type, ttl, jitter, loader);
                localCache.put(key, encoded.json());
                return encoded.data();
            } finally {
                stringRedisTemplate.delete(lockKey);
            }
        }
        // 没拿到锁：短暂重试读缓存
        for (int i = 0; i < LOCK_RETRY; i++) {
            try {
                Thread.sleep(LOCK_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                localCache.put(key, json);
                return decode(key, json, type);
            }
        }
        // 兜底：直接回源（不阻塞）
        Encoded<T> encoded = loadAndCache(key, type, ttl, jitter, loader);
        localCache.put(key, encoded.json());
        return encoded.data();
    }

    private <T> Encoded<T> loadAndCache(String key, Class<T> type, long ttl, long jitter, Supplier<T> loader) {
        T data = loader.get();
        return encodeAndCache(key, data, ttl, jitter);
    }

    private <T> Encoded<T> encodeAndCache(String key, T data, long ttl, long jitter) {
        long actualTtl = data == null ? NULL_TTL : ttl + RANDOM.nextLong(jitter + 1);
        String value = data == null ? NULL_TAG : writeJson(data);
        stringRedisTemplate.opsForValue().set(key, value, actualTtl, TimeUnit.SECONDS);
        return new Encoded<>(value, data);
    }

    /** 反序列化结果与写入 Redis 的原文（避免为写本地缓存重复序列化） */
    private record Encoded<T>(String json, T data) {
    }

    /**
     * M7：区分"空值缓存"与"解码故障"。
     * <ul>
     *   <li>{@code NULL_TAG} → 返回 null（正常的空值缓存，防穿透）</li>
     *   <li>解码失败 → 删除损坏 key（自愈，下次请求回源）、{@code log.error} 并抛出</li>
     * </ul>
     */
    private <T> T decode(String key, String json, Class<T> type) {
        if (NULL_TAG.equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            evictBroken(key, json, e, type.getSimpleName());
            throw new IllegalStateException("缓存数据损坏，无法反序列化为 " + type.getSimpleName(), e);
        }
    }

    private <T> List<T> decodeList(String key, String json, Class<T> elementType) {
        if (NULL_TAG.equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            evictBroken(key, json, e, "List<" + elementType.getSimpleName() + ">");
            throw new IllegalStateException("缓存数据损坏，无法反序列化为 List<" + elementType.getSimpleName() + ">", e);
        }
    }

    /** M7：删除损坏的 key 并告警，使下一次请求自动回源（自愈），同时让本次故障可见 */
    private void evictBroken(String key, String json, Exception cause, String targetType) {
        log.error("[M7] 缓存解码失败，删除损坏 key 以便回源。key={}, target={}, value={}, err={}",
                key, targetType, json, cause.getMessage(), cause);
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[M7] 删除损坏 key 失败, key={}, err={}", key, e.getMessage());
        }
        localCache.invalidate(key);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("cache serialize fail", e);
        }
    }
}