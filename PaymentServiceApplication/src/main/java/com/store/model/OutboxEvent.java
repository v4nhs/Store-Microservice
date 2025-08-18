package com.store.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="outbox_events",
        indexes={
            @Index(name="idx_outbox_status", columnList="status"),
            @Index(name="idx_outbox_created", columnList="createdAt")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tham chiếu
    @Column(nullable=false)
    private String aggregateType; // "PAYMENT"
    @Column(nullable=false)
    private String aggregateId;   // payment.id hoặc orderId

    // Loại event: PAYMENT_SUCCEEDED | PAYMENT_FAILED
    @Column(nullable=false)
    private String eventType;

    @Lob @Column(nullable=false)
    private String payload;

    @Column(nullable=false)
    private String status; // NEW|SENT|FAILED
    @Column(nullable=false)
    private Instant createdAt;
    private Instant publishedAt;
    private String lastError;
}