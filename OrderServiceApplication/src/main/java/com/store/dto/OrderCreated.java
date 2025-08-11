package com.store.dto;

import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreated {
    private String orderId;
    private String userId;
    private String productId;
    private int quantity;
}

