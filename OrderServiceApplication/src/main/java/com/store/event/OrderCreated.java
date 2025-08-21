package com.store.event;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreated {
    private String orderId;
    private String userId;
    private BigDecimal totalAmount;
    @Singular("item")
    private List<Item> items;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Item {
        private String productId;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}


