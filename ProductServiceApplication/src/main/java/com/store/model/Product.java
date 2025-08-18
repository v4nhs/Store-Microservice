package com.store.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @NotBlank(message = "Không để trống tên sản phẩm")
    private String name;
    @NotBlank(message = "Không để trống giá sản phẩm")
    private Double price;
    @NotNull(message = "Không để trống số lượng")
    @Min(value = 1, message = "Quantity phải >= 1")
    private int quantity;
}