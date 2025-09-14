package com.store.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.store.config.BigDecimalPlainSerializer;
import com.store.model.ProductSize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreatedEvent {
    private String id;
    private String name;
    private String image;
    private List<String> sizes = new ArrayList<>();
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    private BigDecimal price;
    private Integer quantity;
}