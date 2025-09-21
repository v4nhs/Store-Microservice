package com.store.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    @JsonProperty("id")
    private String orderId;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderItemDTO> items;
}
