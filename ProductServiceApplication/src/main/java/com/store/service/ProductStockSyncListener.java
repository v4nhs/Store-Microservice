package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.StockReserved;
import com.store.dto.ReleaseStock;
import com.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductStockSyncListener {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper = new ObjectMapper(); // parse JSON

    /** Khi kho đã reserve thành công -> trừ thêm số lượng ở bảng product */
    @Transactional
    @KafkaListener(topics = "stock-reserved", groupId = "product-projection-group")
    public void onStockReserved(String payload) {
        try {
            StockReserved evt = objectMapper.readValue(payload, StockReserved.class);
            log.info("[PROD] Sync on stock-reserved: {}", evt);
            int updated = productRepository.reserve(evt.getProductId(), evt.getQuantity());
            if (updated == 1) {
                log.info("[PROD] Product.quantity -{} for productId={}", evt.getQuantity(), evt.getProductId());
            } else {
                log.warn("[PROD] Reserve failed on product table. pid={}, qty={}", evt.getProductId(), evt.getQuantity());
            }
        } catch (Exception e) {
            log.error("[PROD] Cannot parse stock-reserved payload: {}", payload, e);
        }
    }

    /** Khi cần hoàn kho -> cộng trả ở bảng product */
    @Transactional
    @KafkaListener(topics = "release-stock", groupId = "product-projection-group")
    public void onReleaseStock(String payload) {
        try {
            ReleaseStock evt = objectMapper.readValue(payload, ReleaseStock.class);
            log.info("[PROD] Sync on release-stock: {}", evt);
            productRepository.release(evt.getProductId(), evt.getQuantity());
            log.info("[PROD] Product.quantity +{} for productId={}", evt.getQuantity(), evt.getProductId());
        } catch (Exception e) {
            log.error("[PROD] Cannot parse release-stock payload: {}", payload, e);
        }
    }
}
