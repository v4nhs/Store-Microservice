package com.store.dto;

import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReserved {
    private String orderId;
    private String productId;
    private int quantity;
}