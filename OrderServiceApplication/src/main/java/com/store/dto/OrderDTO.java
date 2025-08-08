package com.store.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderDTO {
    private String orderId;
    private String userId;
    private String productId;
    private int quantity;
    private String status;

}
