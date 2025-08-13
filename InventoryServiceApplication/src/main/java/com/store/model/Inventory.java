package com.store.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {
    @Id
    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(nullable=false)
    private Integer quantity;
}