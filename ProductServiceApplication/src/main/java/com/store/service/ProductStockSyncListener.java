package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.StockReserved;
import com.store.dto.ReleaseStock;
import com.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductStockSyncListener {
    private final ProductRepository productRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "stock-reserved", groupId = "product-projection-group")
    @Transactional
    public void onStockReserved(String payload) {
        try {
            StockReserved evt = objectMapper.readValue(payload, StockReserved.class);

            String key = "proj:product:reserved:" + evt.getOrderId();
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) return;

            int updated = productRepository.reserve(evt.getProductId(), evt.getQuantity());
            if (updated != 1) {
                redis.delete(key);
            }
        } catch (Exception e) {
            log.error("[PROD] Cannot parse stock-reserved payload: {}", payload, e);
        }
    }

    @KafkaListener(topics = "release-stock", groupId = "product-projection-group")
    @Transactional
    public void onReleaseStock(String payload) {
        try {
            ReleaseStock evt = objectMapper.readValue(payload, ReleaseStock.class);

            String key = "proj:product:released:" + evt.getOrderId();
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) return;

            productRepository.release(evt.getProductId(), evt.getQuantity());
        } catch (Exception e) {
            log.error("[PROD] Cannot parse release-stock payload: {}", payload, e);
        }
    }
}

