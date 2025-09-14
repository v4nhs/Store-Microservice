package com.store.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductUpdateEvent(
        String id,
        String name,
        String image,
        List<String> sizes,
        BigDecimal price,
        int quantity
) {}
