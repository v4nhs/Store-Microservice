package com.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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