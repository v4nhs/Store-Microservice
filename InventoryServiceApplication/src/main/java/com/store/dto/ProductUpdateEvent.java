package com.store.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductUpdateEvent {
    private String productId;
    private String name;
    private BigDecimal price;
    private int quantity;
}
