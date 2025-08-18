package com.store.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.StockRejected;
import com.store.dto.StockReserved;
import com.store.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository repo;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper om = new ObjectMapper();
    private final com.store.projection.StockProjector projector;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void flushOutbox() {
        var batch = repo.findTop100ByStatusOrderByIdAsc("NEW");
        if (batch.isEmpty()) return;

        for (var e : batch) {
            try {
                switch (e.getEventType()) {
                    case "STOCK_RESERVED" -> {
                        var reserved = om.readValue(e.getPayload(), StockReserved.class);
                        projector.projectReserved(e.getId(), reserved.getProductId(), reserved.getQuantity());
                        kafka.send("stock-reserved", reserved);
                    }
                    case "STOCK_REJECTED" -> {
                        var rejected = om.readValue(e.getPayload(), StockRejected.class);
                        kafka.send("stock-rejected", rejected);
                    }
                    case "STOCK_RELEASED" -> {
                        var node = om.readTree(e.getPayload());
                        projector.projectReleased(e.getId(),
                                node.get("productId").asText(),
                                node.get("quantity").asInt());
                        kafka.send("stock-released", node);
                    }
                    case "PRODUCT_STOCK_DECREASED" -> {
                        var node = om.readTree(e.getPayload());
                        kafka.send("product-stock-decreased", node);
                    }
                    case "ORDER_CONFIRMED" -> {
                        var node = om.readTree(e.getPayload());
                        kafka.send("order-confirmed", node);
                    }
                    case "ORDER_CANCELLED" -> {
                        var node = om.readTree(e.getPayload());
                        kafka.send("order-cancelled", node);
                    }
                    default -> log.warn("Unknown eventType: {}", e.getEventType());
                }
                e.setStatus("SENT");
                log.info("[OUTBOX] SENT {} for {}", e.getEventType(), e.getAggregateId());
            } catch (Exception ex) {
                e.setStatus("FAILED");
                e.setLastError(ex.getMessage());
                log.error("Outbox publish failed id={}: {}", e.getId(), ex.getMessage());
            }
        }
        repo.saveAll(batch);
    }
}
