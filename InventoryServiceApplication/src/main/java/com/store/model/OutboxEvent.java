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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;
    private String aggregateId;
    private String eventType;
    @Lob
    private String payload;
    private String status;
    @Lob
    private String lastError;
}