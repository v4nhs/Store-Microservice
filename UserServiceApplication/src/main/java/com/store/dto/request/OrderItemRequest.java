package com.store.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OrderItemRequest {
    @NotBlank
    private String productId;
    @NotBlank
    private String size;
    @Min(1)
    private Integer quantity;
}
