package com.store.event;

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
}
