package com.store.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderRequest {
    @NotBlank(message = "userId không được trống")
    private String userId;
    @NotBlank(message = "productId không được trống")
    private String productId;
    @NotNull(message = "quantity là bắt buộc")
    @Min(value = 1, message = "quantity phải >= 1")
    private Integer quantity;
}
