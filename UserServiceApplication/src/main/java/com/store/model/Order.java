package com.store.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.function.BiConsumer;

@Data
public class Order {
    private String id;
    private String userId;
    private String productId;
    private int quantity;
    private String status;
    private BigDecimal totalAmount;
}