package com.store.controller;

import com.store.dto.OrderDTO;
import com.store.dto.request.OrderCreateRequest;
import com.store.dto.request.OrderItemRequest;
import com.store.model.Order;
import com.store.dto.request.OrderRequest;
import com.store.security.JwtUtil;
import com.store.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final OrderService orderService;

    @PostMapping("/create")
    public ResponseEntity<OrderDTO> createOrder(@RequestBody List<OrderItemRequest> items,
                                                HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Cắt "Bearer " để lấy token
        String token = header.substring(7);

        // Truyền token (String) vào JwtUtil
        String userId = jwtUtil.extractUserId(token);

        // Tạo order
        Order order = orderService.createOrder(userId, items);
        return ResponseEntity.ok(orderService.toDto(order));
    }

    private ResponseEntity<Map<String,Object>> bad(String msg) {
        Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("message", msg);
        return ResponseEntity.badRequest().body(body);
    }

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrderDtos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable("id") String id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }
    @GetMapping("/mine")
    public ResponseEntity<?> myOrders(HttpServletRequest httpReq) {
        String auth = httpReq.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return bad("Missing Authorization header (Bearer)");
        }
        String token = auth.substring(7);

        final String userId;
        try {
            userId = jwtUtil.extractUserId(token);
            java.util.UUID.fromString(userId);
        } catch (Exception e) {
            return bad("Invalid token or userId: " + e.getClass().getSimpleName());
        }

        var orders = orderService.getAllByUser(userId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/mine/{id}")
    public ResponseEntity<?> myOrderById(@PathVariable("id") String id, HttpServletRequest httpReq) {
        String auth = httpReq.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return bad("Missing Authorization header (Bearer)");
        }
        String token = auth.substring(7);

        final String oid, uid;
        try {
            uid = jwtUtil.extractUserId(token);
            oid = id;
        } catch (Exception e) {
            return bad("Invalid token or orderId: " + e.getClass().getSimpleName());
        }

        var order = orderService.getByIdForUser(oid, uid);
        if (order == null) return ResponseEntity.status(404).body(Map.of("message","Order not found"));
        return ResponseEntity.ok(order);
    }

}
