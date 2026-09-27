package com.litemall.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * M16：二级缓存按需装配——仅当 classpath 存在 Redis 时才注册 {@link CacheService}。
 * <p>
 * 网关（WebFlux）排除了 Redis 依赖，若 {@code CacheService} 仍以 {@code @Component}
 * 被组件扫描，网关启动会抛 {@code NoClassDefFoundError: StringRedisTemplate}。
 * <p>
 * M8：同时装配本地缓存失效广播的订阅端——任一实例删除/失效 key 时，
 * 其他实例同步清理自己的 Caffeine，避免多节点下最长 60s 的读不一致。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new CacheService(stringRedisTemplate, objectMapper);
    }

    /**
     * M8：订阅 {@link CacheService#INVALIDATE_CHANNEL}，收到消息后只清理本机 Caffeine。
     * <p>
     * 订阅端不再触发二次广播，避免消息回环。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisMessageListenerContainer cacheInvalidateListenerContainer(RedisConnectionFactory connectionFactory,
                                                                         CacheService cacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            if (payload.startsWith(CacheService.MSG_KEY)) {
                cacheService.evictLocal(payload.substring(CacheService.MSG_KEY.length()));
            } else if (payload.startsWith(CacheService.MSG_PREFIX)) {
                cacheService.evictLocalByPrefix(payload.substring(CacheService.MSG_PREFIX.length()));
            }
        }, new ChannelTopic(CacheService.INVALIDATE_CHANNEL));
        return container;
    }
}