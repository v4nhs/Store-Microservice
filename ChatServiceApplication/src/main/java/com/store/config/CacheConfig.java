package com.store.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.cache.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory cf, RedisCacheConfiguration cfg) {
        return RedisCacheManager.builder(cf)
                .cacheDefaults(cfg)
                .withInitialCacheConfigurations(Map.of(
                        "productById", cfg,
                        "productByName", cfg
                ))
                .transactionAware()
                .build();
    }
}
