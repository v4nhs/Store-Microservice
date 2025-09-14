package com.store.event;

import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRejected {
    private String orderId;
    private String productId;
    private String size;
    private int requested;
    private String reason;
}