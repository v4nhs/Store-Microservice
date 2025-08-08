package com.store.controller;

import com.store.dto.OrderDTO;
import com.store.model.Order;
import com.store.request.OrderRequest;
import com.store.security.JwtUtil;
import com.store.service.OrderKafkaProducer;
import com.store.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request,
                                              @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.extractUserId(token.replace("Bearer ", ""));
        request.setUserId(userId);

        Order order = orderService.createOrder(request);

        // Lưu trạng thái vào Redis
        System.out.println("Saving to Redis: key=order:" + order.getId() + ", value=" + order.getStatus());
        redisTemplate.opsForValue().set("order:" + order.getId(), order.getStatus());

        return ResponseEntity.ok(order);
    }
}
