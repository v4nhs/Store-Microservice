package com.store.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;
    private String aggregateId;     // orderId
    private String eventType;       // STOCK_RESERVED / STOCK_REJECTED / STOCK_RELEASED
    @Lob
    private String payload;
    private String status;          // NEW / SENT / FAILED
    @Lob
    private String lastError;
}