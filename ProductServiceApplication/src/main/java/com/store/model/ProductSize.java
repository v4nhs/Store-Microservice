package com.store.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(
        name = "product_size",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "size"})
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Access(AccessType.FIELD)
@ToString(exclude = "product")
public class ProductSize {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @NotBlank(message = "Size không được để trống")
    @Column(name = "size", nullable = false, length = 20)
    private String size;
    @Min(0)
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
