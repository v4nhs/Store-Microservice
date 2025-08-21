package com.store.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.store.config.BigDecimalPlainSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

    @NotNull(message = "Không để trống giá sản phẩm")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0")
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    @Column(precision = 38, scale = 6)
    private BigDecimal price;

    @NotNull(message = "Không để trống số lượng")
    @Min(value = 1, message = "Quantity phải >= 1")
    private int quantity;
}