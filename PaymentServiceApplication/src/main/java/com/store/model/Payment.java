package com.store.model;


import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "payments",
        indexes = {
                @Index(name="ux_payment_order", columnList="orderId", unique = true),
                @Index(name="ux_payment_idem",  columnList="idempotencyKey", unique = true)
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String orderId;
    @Column(nullable=false)
    private String userId;
    @Column(nullable=false)
    private Long amount;
    @Column(nullable=false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private PaymentStatus status; // PENDING|SUCCEEDED|FAILED

    private String gatewayTxnId;

    @Column(length=128)
    private String idempotencyKey; // <- chống trùng
    @Column(nullable=false)
    private Instant createdAt;
    @Column(nullable=false)
    private Instant updatedAt;

    public boolean isTerminal() {
        return status==PaymentStatus.SUCCEEDED || status==PaymentStatus.FAILED;
    }
}