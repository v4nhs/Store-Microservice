package com.store.controller;

import com.store.model.Order;
import com.store.request.OrderRequest;
import com.store.security.JwtUtil;
import com.store.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request,
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
