package com.store.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailed {
    private String orderId;
    private String paymentId;
    private String reason;
}
