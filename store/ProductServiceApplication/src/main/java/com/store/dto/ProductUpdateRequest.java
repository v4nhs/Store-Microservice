package com.store.dto;

import lombok.Data;

@Data
public class ProductUpdateRequest {
    private String name;
    private String image;
    private String keyword;
    private double price;
    private int quantity;
}