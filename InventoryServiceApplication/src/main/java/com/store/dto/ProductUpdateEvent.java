package com.store.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductUpdateEvent {
    @JsonAlias({"id"})
    private String productId;
    private String name;
    private BigDecimal price;
    private int quantity;
}
