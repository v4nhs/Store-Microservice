package com.store.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class PaymentSucceeded {
    private String orderId;
    private String paymentId;
    private BigDecimal amount;
    private String status;
    private String email;
    private String customerName;
    private String method;
    private String provider;
    private String transactionRef;
}
