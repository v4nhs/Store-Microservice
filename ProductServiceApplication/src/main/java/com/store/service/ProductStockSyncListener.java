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

    // idempotency key theo từng ITEM, không phải theo ORDER
    private String reservedKey(String orderId, String productId) {
        return "proj:product:reserved:" + orderId + "#" + productId;
    }
    private String releasedKey(String orderId, String productId) {
        return "proj:product:released:" + orderId + "#" + productId;
    }

    @KafkaListener(topics = "stock-reserved", groupId = "product-projection-group")
    @Transactional
    public void onStockReserved(String payload) {
        try {
            StockReserved evt = objectMapper.readValue(payload, StockReserved.class);

            // idempotency theo từng item
            String key = reservedKey(evt.getOrderId(), evt.getProductId());
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                log.info("[PROD] Skip duplicate reserved for {}#{}", evt.getOrderId(), evt.getProductId());
                return;
            }

            int updated = productRepository.reserve(evt.getProductId(), evt.getQuantity());
            if (updated != 1) {
                // thất bại -> cho phép retry lần sau
                redis.delete(key);
                log.warn("[PROD] reserve() did not update row (updated={}) for productId={}, qty={}, orderId={}",
                        updated, evt.getProductId(), evt.getQuantity(), evt.getOrderId());
            } else {
                log.info("[PROD] Reserved OK productId={}, qty={} (orderId={})",
                        evt.getProductId(), evt.getQuantity(), evt.getOrderId());
            }
        } catch (Exception e) {
            log.error("[PROD] Cannot parse/process stock-reserved payload: {}", payload, e);
        }
    }

    @KafkaListener(topics = "release-stock", groupId = "product-projection-group")
    @Transactional
    public void onReleaseStock(String payload) {
        try {
            ReleaseStock evt = objectMapper.readValue(payload, ReleaseStock.class);

            String key = releasedKey(evt.getOrderId(), evt.getProductId());
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                log.info("[PROD] Skip duplicate release for {}#{}", evt.getOrderId(), evt.getProductId());
                return;
            }

            int updated = productRepository.release(evt.getProductId(), evt.getQuantity());
            if (updated != 1) {
                // thất bại -> cho phép retry lần sau
                redis.delete(key);
                log.warn("[PROD] release() did not update row (updated={}) for productId={}, qty={}, orderId={}",
                        updated, evt.getProductId(), evt.getQuantity(), evt.getOrderId());
            } else {
                log.info("[PROD] Released OK productId={}, qty={} (orderId={})",
                        evt.getProductId(), evt.getQuantity(), evt.getOrderId());
            }
        } catch (Exception e) {
            log.error("[PROD] Cannot parse/process release-stock payload: {}", payload, e);
        }
    }
}
