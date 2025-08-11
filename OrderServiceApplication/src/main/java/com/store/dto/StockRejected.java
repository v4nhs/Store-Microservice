package com.store.dto;

import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRejected {
    private String orderId;
    private String productId;
    private int requested;
    private String reason;
}