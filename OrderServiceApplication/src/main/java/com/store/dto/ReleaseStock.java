package com.store.dto;

import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseStock {
    private String orderId;
    private String productId;
    private int quantity;
    private String reason;
}
