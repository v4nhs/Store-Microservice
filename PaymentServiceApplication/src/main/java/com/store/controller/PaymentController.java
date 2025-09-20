package com.store.controller;

import com.store.dto.PaymentRequest;
import com.store.model.Payment;
import com.store.client.PayPalClient;
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
    private final PayPalClient payPalClient;

    @PostMapping("/pay")
    public ResponseEntity<Payment> pay(@RequestBody @Valid PaymentRequest req,
                                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Incoming /pay: orderId={}, amount={}, idemKey={}, method={}, provider={}, returnUrl={}",
                req.getOrderId(), req.getAmount(), req.getIdempotencyKey(), req.getMethod(), req.getProvider(), req.getReturnUrl());

        Payment payment = paymentService.pay(req, authHeader);
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/paypal/capture")
    public ResponseEntity<Payment> capture(@RequestParam("paypalOrderId") String paypalOrderId) {
        Payment p = paymentService.capturePaypal(paypalOrderId);
        return ResponseEntity.ok(p);
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
