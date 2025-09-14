package com.store.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.store.config.BigDecimalPlainSerializer;
import io.micrometer.common.lang.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "product")
@Access(AccessType.FIELD)
@ToString(exclude = "sizes")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Không để trống tên sản phẩm")
    private String name;

    @NotBlank(message = "Không để trống ảnh sản phẩm")
    private String image;
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductSize> sizes = new ArrayList<>();

    @NotNull(message = "Không để trống giá sản phẩm")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá sản phẩm phải lớn hơn 0")
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    @Column(precision = 38, scale = 6)
    private BigDecimal price;

    @Min(value = 0, message = "Quantity phải >= 0")
    @Column(nullable = false)
    private Integer quantity = 0;


    public void setSizes(List<ProductSize> sizes) {
        this.sizes.clear();
        if (sizes != null) {
            for (ProductSize s : sizes) {
                if (s == null) continue;
                if (s.getQuantity() == null) s.setQuantity(0);
                if (s.getQuantity() < 0) s.setQuantity(0);
                s.setProduct(this);
                this.sizes.add(s);
            }
        }
        recalcQuantityFromSizes();
    }
    @PrePersist @PreUpdate
    private void _syncBeforeSave() {
        if (sizes != null) {
            for (var ps : sizes) {
                if (ps.getQuantity() < 0) ps.setQuantity(0);
                ps.setProduct(this);
            }
        }
        recalcQuantityFromSizes();
    }
    public int recalcQuantityFromSizes() {
        int sum = (sizes == null) ? 0 : sizes.stream()
                .filter(Objects::nonNull)
                .mapToInt(ps -> Math.max(0, ps.getQuantity()))
                .sum();
        this.quantity = sum;
        return sum;
    }
}
