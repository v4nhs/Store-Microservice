package com.store.controller;

import com.store.dto.request.OrderCreateRequest;
import com.store.model.Order;
import com.store.dto.request.OrderRequest;
import com.store.security.JwtUtil;
import com.store.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
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
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderCreateRequest req,
                                         HttpServletRequest httpReq) {
        System.out.println("[ORDER][INCOMING] " + req);

        // 1) Lấy Authorization từ header gốc (ổn định hơn @RequestHeader khi qua proxy/gateway)
        String auth = httpReq.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return bad("Missing Authorization header (Bearer)");
        }
        String token = auth.substring(7);

        // 2) Trích userId từ JWT và GHI ĐÈ body
        String userIdFromJwt;
        try {
            userIdFromJwt = jwtUtil.extractUserId(token); // dùng JwtUtil của bạn
            if (userIdFromJwt == null || userIdFromJwt.isBlank()) {
                return bad("Missing userId claim in token");
            }
            // Làm sạch & validate UUID
            userIdFromJwt = java.text.Normalizer.normalize(userIdFromJwt, java.text.Normalizer.Form.NFKC)
                    .strip()
                    .replace("\uFEFF","").replace("\u200B","").replace("\u200E","").replace("\u200F","");
            java.util.UUID.fromString(userIdFromJwt);
        } catch (Exception e) {
            return bad("Invalid token or userId: " + e.getClass().getSimpleName());
        }
        req.setUserId(userIdFromJwt);

        // 3) Validate items/size
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return bad("Danh sách items trống");
        }
        for (var it : req.getItems()) {
            if (it.getSize() == null || it.getSize().isBlank()) {
                return bad("Thiếu size cho productId=" + it.getProductId());
            }
        }

        // 4) Tạo order như cũ
        Order order = orderService.createOrder(req);

        // 5) Cache trạng thái vào Redis như bạn đang làm...
        String key = "order:" + order.getId();
        redisTemplate.opsForValue().set(key, order.getStatus().toString(), java.time.Duration.ofDays(7));

        return ResponseEntity.ok(order);
    }

    // Helper trả JSON message thay vì [no body]
    private ResponseEntity<Map<String,Object>> bad(String msg) {
        Map<String,Object> body = new java.util.LinkedHashMap<>();
        body.put("message", msg);
        return ResponseEntity.badRequest().body(body);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
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
        return ResponseEntity.ok(orders); // [] nếu chưa có
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
