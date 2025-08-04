package com.store.model;

import lombok.Data;

@Data
public class Order {
    private String id;
    private String userId;
    private String productId;
    private int quantity;
    private String status;
}