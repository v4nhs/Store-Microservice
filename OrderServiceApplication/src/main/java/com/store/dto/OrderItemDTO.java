package com.store.dto;
import lombok.*;

import java.math.BigDecimal;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO {
    private String productId;
    private String size;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
}