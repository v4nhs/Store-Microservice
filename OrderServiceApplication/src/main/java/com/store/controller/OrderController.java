package com.store.controller;

import com.store.dto.request.OrderCreateRequest;
import com.store.model.Order;
import com.store.dto.request.OrderRequest;
import com.store.security.JwtUtil;
import com.store.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final OrderService orderService;

    // Tạo 1 order (1 product)
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request,
                                             @RequestHeader("Authorization") String token) {
        String raw = (token != null && token.startsWith("Bearer ")) ? token.substring(7) : token;
        String userId = jwtUtil.extractUserId(raw);
        request.setUserId(userId);

        Order order = orderService.createOrder(request);

        String key = "order:" + order.getId();
        redisTemplate.opsForValue().set(key, order.getStatus().toString(), Duration.ofDays(7));

        return ResponseEntity.ok(order);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<Order>> createOrders(@Valid @RequestBody List<@Valid OrderRequest> requests,
                                                    @RequestHeader("Authorization") String token) {
        String raw = (token != null && token.startsWith("Bearer ")) ? token.substring(7) : token;
        String userId = jwtUtil.extractUserId(raw);
        requests.forEach(r -> r.setUserId(userId));

        List<Order> saved = orderService.createOrder(requests);

        saved.forEach(o -> {
            String key = "order:" + o.getId();
            redisTemplate.opsForValue().set(key, o.getStatus().toString(), Duration.ofDays(7));
        });

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/multi")
    public ResponseEntity<Order> createOrderMulti(@Valid @RequestBody OrderCreateRequest req,
                                                  @RequestHeader("Authorization") String token) {
        String raw = (token != null && token.startsWith("Bearer ")) ? token.substring(7) : token;
        String userId = jwtUtil.extractUserId(raw);
        req.setUserId(userId);

        Order order = orderService.createOrderMulti(req);

        String key = "order:" + order.getId();
        redisTemplate.opsForValue().set(key, order.getStatus().toString(), Duration.ofDays(7));

        return ResponseEntity.ok(order);
    }
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable("id") String id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }
}
