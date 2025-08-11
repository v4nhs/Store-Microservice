package com.store.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductDTO {
    private String id;
    private String name;
    private double price;
}
