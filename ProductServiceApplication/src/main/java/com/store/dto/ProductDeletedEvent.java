package com.store.dto;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDeletedEvent {
    private String productId;
}