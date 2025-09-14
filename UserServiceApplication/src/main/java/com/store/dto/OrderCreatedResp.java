package com.store.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedResp {
    private String id;
    private String userId;
    private String productId;
    private String size;
    private Integer quantity;
    private String status;
}