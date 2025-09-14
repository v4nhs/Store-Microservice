package com.store.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String productId;
    @Column(name = "size", nullable = false, length = 20)
    private String size;
    @NotNull(message = "Quantity không được null")
    @Min(value = 1, message = "Quantity phải >= 1")
    private Integer quantity;
    @Column(precision = 38, scale = 10)
    private BigDecimal unitPrice;
    @Column(precision = 38, scale = 10)
    private BigDecimal lineAmount;

    @Enumerated(EnumType.STRING)
    private OrderItemStatus itemStatus; // PENDING / RESERVED / REJECTED

    @JsonIgnore
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
}
