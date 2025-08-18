package com.store.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductUpdateEvent {
    private String productId;
    private String name;
    private double price;
    private int quantity;
}
