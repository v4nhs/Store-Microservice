package com.store.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductCreatedEvent(
        String id,
        String name,
        String image,
        BigDecimal price,
        Integer quantity,
        List<String> sizes,
        List<SizeQty> sizesWithQty
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record SizeQty(
            String size,
            Integer quantity
    ) {}
}