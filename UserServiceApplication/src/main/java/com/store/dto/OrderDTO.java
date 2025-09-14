package com.store.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDTO {
    private String id;
    private String userId;
    private String productId;
    private String size;
    private Integer quantity;
    private String status;
    private BigDecimal totalAmount;

}