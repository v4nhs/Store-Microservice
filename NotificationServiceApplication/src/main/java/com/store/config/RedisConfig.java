package com.store.config;

import com.store.dto.OrderEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, OrderEvent> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, OrderEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }
}
