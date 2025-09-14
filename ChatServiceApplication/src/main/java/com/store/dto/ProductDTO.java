package com.store.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.store.config.BigDecimalPlainSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDTO {
    private String id;
    private String name;
    private String image;
    private List<ProductDTO> sizes = new ArrayList<>();
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    private BigDecimal price;
    private Integer quantity;
}
