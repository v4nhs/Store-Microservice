package com.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

