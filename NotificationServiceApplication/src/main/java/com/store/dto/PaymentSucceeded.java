package com.store.dto;
import lombok.Data;

@Data
public class PaymentSucceeded {
    private String orderId;
    private String paymentId;
    private double amount;
    private String status;
    private String email;
    private String customerName;
    private String method;
    private String provider;
    private String transactionRef;
}