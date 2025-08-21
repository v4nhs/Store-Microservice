package com.store.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.PaymentFailed;
import com.store.dto.PaymentSucceeded;
import com.store.model.OutboxEvent;
import com.store.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository repo;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper om = new ObjectMapper();

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void flush() {
        List<OutboxEvent> batch = repo.findTop100ByStatusOrderByIdAsc("NEW");
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            try {
                switch (e.getEventType()) {
                    case "PAYMENT_SUCCESS" -> {
                        PaymentSucceeded evt = om.readValue(e.getPayload(), PaymentSucceeded.class);
                        kafka.send("payment-success", evt);
                        kafka.send("payment-succeeded", evt);
                    }
                    case "PAYMENT_FAILED" -> {
                        PaymentFailed evt = om.readValue(e.getPayload(), PaymentFailed.class);
                        kafka.send("payment-failed", evt);
                    }
                    default -> log.warn("Unknown eventType: {}", e.getEventType());
                }
                e.setStatus("SENT");
                e.setLastError(null);
            } catch (Exception ex) {
                e.setStatus("FAILED");
                e.setLastError(ex.getMessage());
                log.error("Publish outbox id={} failed: {}", e.getId(), ex.getMessage());
            }
        }
        repo.saveAll(batch);
    }
}
