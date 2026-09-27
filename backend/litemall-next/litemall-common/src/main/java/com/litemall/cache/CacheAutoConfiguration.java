package com.litemall.cache;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * M16：二级缓存按需装配——仅当 classpath 存在 Redis 时才注册 {@link CacheService}。
 * <p>
 * 网关（WebFlux）排除了 Redis 依赖，若 {@code CacheService} 仍以 {@code @Component}
 * 被组件扫描，网关启动会抛 {@code NoClassDefFoundError: StringRedisTemplate}。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        return new CacheService(stringRedisTemplate, objectMapper);
    }
}
