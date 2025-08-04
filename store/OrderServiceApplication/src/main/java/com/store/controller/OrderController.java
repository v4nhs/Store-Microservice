package com.store.controller;

import com.store.dto.OrderDto;
import com.store.model.Order;
import com.store.request.OrderRequest;
import com.store.security.JwtUtil;
import com.store.service.OrderKafkaProducer;
import com.store.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderKafkaProducer orderKafkaProducer;
    @PostMapping
    public ResponseEntity<Order> placeOrder(
            @RequestBody OrderRequest request,
            @RequestHeader("Authorization") String token) {

        String userId = JwtUtil.extractUserId(token);
        request.setUserId(userId); // gán userId từ token vào request

        // xử lý lưu order như bình thường
        Order order = orderService.createOrder(request);
        return ResponseEntity.ok(order);
    }

}

