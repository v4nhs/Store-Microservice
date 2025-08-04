package com.store.dto;

import lombok.Data;

@Data
public class OrderDto {
    private String userId;
    private String productId;
    private int quantity;
    private String status;

}
