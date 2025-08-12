package com.store.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
    @Bean
    public DefaultRedisScript<Long> reserveScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setResultType(Long.class);
        s.setScriptText("""
            -- KEYS[1]=stock:{productId}, KEYS[2]=order:seen:{orderId}
            -- ARGV[1]=orderId, ARGV[2]=quantity, ARGV[3]=ttlSeenSeconds
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 2
            end
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            local need = tonumber(ARGV[2])
            if stock >= need then
              redis.call('DECRBY', KEYS[1], need)
              redis.call('SETEX', KEYS[2], tonumber(ARGV[3]), ARGV[1])
              return 1
            else
              return 0
            end
        """);
        return s;
    }
    @Bean
    public DefaultRedisScript<Long> releaseScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setResultType(Long.class);
        s.setScriptText("""
            -- KEYS[1]=stock:{productId}, KEYS[2]=order:seen:{orderId}
            -- ARGV[1]=orderId, ARGV[2]=quantity
            redis.call('INCRBY', KEYS[1], tonumber(ARGV[2]))
            redis.call('DEL', KEYS[2])
            return 1
        """);
        return s;
    }
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}

