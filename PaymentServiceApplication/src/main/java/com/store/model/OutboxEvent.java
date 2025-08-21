package com.store.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name="outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // dùng để orderByIdAsc

    @Column(nullable = false)
    private String aggregateId;         // paymentId

    @Column(nullable = false)
    private String aggregateType;       // "PAYMENT"

    @Column(nullable = false)
    private String eventType;           // "PAYMENT_SUCCESS" | "PAYMENT_FAILED"

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private String status;              // "NEW" | "SENT" | "FAILED"

    private String lastError;

    @CreationTimestamp
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (status == null) status = "NEW";
    }
}