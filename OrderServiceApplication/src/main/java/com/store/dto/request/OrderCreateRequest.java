package com.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
@Data
public class OrderCreateRequest {
    private String userId;
    @NotBlank
    private String size;
    @NotEmpty
    private List<OrderItemRequest> items;
}
