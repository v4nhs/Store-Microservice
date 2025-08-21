package com.store.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
@Data
public class OrderCreateRequest {
    private String userId;
    @NotEmpty
    private List<OrderItemRequest> items;
}
