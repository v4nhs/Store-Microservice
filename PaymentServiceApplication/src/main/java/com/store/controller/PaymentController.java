package com.store.controller;

import com.store.security.JwtUtil;
import com.store.service.PaymentService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final JwtUtil jwt;
    private final PaymentService service;
    private final RestTemplate rest;

    @Value("${services.order.baseUrl}") private String orderBaseUrl;

    @PostMapping("/orders/{orderId}/charge")
    public ResponseEntity<?> chargeByOrderId(@PathVariable String orderId,
                                             @RequestParam(defaultValue = "true") boolean success,
                                             @RequestHeader("Authorization") String auth,
                                             @RequestHeader(value="X-Idempotency-Key", required=false) String idemKey) throws Exception {
        // 1) user hiện tại
        String userId = jwt.extractUserId(auth);

        // 2) lấy đơn & xác thực chủ sở hữu
        ResponseEntity<OrderView> resp = rest.getForEntity(orderBaseUrl + "/api/orders/{id}", OrderView.class, orderId);
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody()==null)
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error","Cannot fetch order","orderId",orderId));

        OrderView order = resp.getBody();
        if (!userId.equals(order.getUserId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Order does not belong to current user"));

        long amount = order.getTotalAmount()==null? 0L : order.getTotalAmount();

        // 3) idempotent create (UNIQUE: orderId + optional idemKey)
        var p = service.createOrGetPendingByOrder(orderId, userId, amount, "VND", idemKey);

        // 4) mock charge
        if (success) {
            service.markSucceeded(orderId, "TXN-"+orderId);
            return ResponseEntity.ok(Map.of("orderId", orderId, "paymentId", "pay_"+p.getId(), "status", "SUCCEEDED", "amount", amount));
        } else {
            service.markFailed(orderId, "USER_CANCELLED");
            return ResponseEntity.ok(Map.of("orderId", orderId, "paymentId", "pay_"+p.getId(), "status", "FAILED", "amount", amount));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderView {
        private String id;
        private String userId;
        private Long totalAmount;
        private String status;
    }
}