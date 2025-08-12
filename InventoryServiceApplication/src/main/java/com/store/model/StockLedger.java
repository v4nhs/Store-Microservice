package com.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;


@Entity
@Table(name="stock_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockLedger {
    @Id
    private Long outboxId;
    private String eventType;
    private String productId;
    private Integer quantity;
    @Column(columnDefinition = "timestamp default current_timestamp")
    private java.sql.Timestamp createdAt;
}