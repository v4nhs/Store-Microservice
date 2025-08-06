package com.store.service;

import com.store.dto.OrderStatusUpdateDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderStatusConsumer {

    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(topics = "order_status_topic", groupId = "order")
    public void updateOrderStatus(OrderStatusUpdateDTO statusUpdate) {
        redisTemplate.opsForValue()
                .set("order:" + statusUpdate.getOrderId(), statusUpdate.getStatus());
    }
}