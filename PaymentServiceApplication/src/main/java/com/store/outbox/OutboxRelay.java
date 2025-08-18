package com.store.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        var batch = repo.findTop100ByStatusOrderByIdAsc("NEW");
        for (var e: batch) {
            try {
                switch (e.getEventType()) {
                    case "PAYMENT_SUCCEEDED" -> kafka.send("payment.succeeded", om.readTree(e.getPayload()));
                    case "PAYMENT_FAILED"    -> kafka.send("payment.failed",    om.readTree(e.getPayload()));
                }
                e.setStatus("SENT"); e.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                e.setStatus("FAILED"); e.setLastError(ex.getMessage());
            }
        }
        repo.saveAll(batch);
    }
}
