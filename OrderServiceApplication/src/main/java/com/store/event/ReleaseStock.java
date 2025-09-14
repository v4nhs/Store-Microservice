package com.store.event;

import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseStock {
    private String orderId;
    private String productId;
    private String size;
    private int quantity;
    private String reason;
}
