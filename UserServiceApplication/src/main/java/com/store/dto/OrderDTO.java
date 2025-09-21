package com.store.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDTO {
    private String id;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderItemDTO> items;
}