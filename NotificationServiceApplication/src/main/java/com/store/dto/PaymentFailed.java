package com.store.dto;

import lombok.Data;

@Data
public class PaymentFailed {
    private String orderId;
    private String customerName;
    private String email;
    private String method;
    private String provider;
    private String transactionRef;
    private String status;
    private String reason;
    private Object amount;
}
