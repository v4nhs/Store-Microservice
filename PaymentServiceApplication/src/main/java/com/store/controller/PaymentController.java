package com.store.controller;

import com.store.dto.PaymentRequest;
import com.store.model.Payment;
import com.store.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Log4j2
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Thanh toán: nhận JSON, chuyển thẳng PaymentRequest vào service.
     * Không còn gọi overload pay(String, BigDecimal, String, ...) để tránh bị set cứng COD.
     */
    @PostMapping("/pay")
    public ResponseEntity<Payment> pay(@RequestBody @Valid PaymentRequest req,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Incoming /pay: orderId={}, amount={}, idemKey={}, method={}, provider={}, returnUrl={}",
                req.getOrderId(), req.getAmount(), req.getIdempotencyKey(), req.getMethod(), req.getProvider(), req.getReturnUrl());

        Payment payment = paymentService.pay(req, authHeader);
        return ResponseEntity.ok(payment);
    }

    @GetMapping(value = "/by-order/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Payment> getByOrderId(@PathVariable("orderId") String orderId) {
        return paymentService.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/status/{orderId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> status(@PathVariable("orderId") String orderId) {
        return paymentService.findByOrderId(orderId)
                .map(p -> ResponseEntity.ok(p.getStatus().name()))
                .orElse(ResponseEntity.notFound().build());
    }
}
