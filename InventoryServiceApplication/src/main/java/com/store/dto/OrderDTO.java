package com.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderDTO {
    private String orderId;
    private String userId;
    private List<OrderItemDTO> items;
    private double totalAmount;
    private String status;
}