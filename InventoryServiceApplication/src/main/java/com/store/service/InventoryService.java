package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.ProductCreatedEvent;
import com.store.dto.ProductUpdateEvent;
import com.store.model.Inventory;
import com.store.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();

    private String stockKey(String productId) { return "stock:" + productId; }

    // ========= product-created => tạo/khởi tạo tồn kho tuyệt đối =========
    @KafkaListener(
            topics = "product-created-topic",
            groupId = "inventory-init-group",
            containerFactory = "productCreatedStringFactory" // giữ nguyên nếu bạn đã cấu hình
    )
    @Transactional
    public void onProductCreated(String payload) {
        try {
            // payload đôi khi bị wrap thành chuỗi JSON
            String json = (payload != null && !payload.isEmpty() && payload.charAt(0) == '"')
                    ? om.readValue(payload, String.class)
                    : payload;

            ProductCreatedEvent evt = om.readValue(json, ProductCreatedEvent.class);

            String productId = evt.getProductId();
            if (productId == null || productId.isBlank()) {
                log.warn("[INV-INIT] productId rỗng trong payload: {}", payload);
                return;
            }
            int initialQty = Math.max(0, evt.getQuantity());

            Inventory inv = inventoryRepository.findByProductId(productId)
                    .orElseGet(() -> Inventory.builder().productId(productId).quantity(0).build());

            int old = inv.getQuantity();
            inv.setQuantity(initialQty);
            inventoryRepository.save(inv);

            redis.opsForValue().set(stockKey(productId), String.valueOf(initialQty));

            log.info("[INV-INIT] Upsert inventory productId={}, {} -> {} (Redis OK)", productId, old, initialQty);
        } catch (Exception e) {
            log.error("[INV-INIT] Cannot handle product-created payload: {}", payload, e);
        }
    }

    // ========= product-updated => đồng bộ tuyệt đối quantity =========
    @KafkaListener(
            topics = "product-updated-topic",
            groupId = "inventory-sync-group",
            containerFactory = "productCreatedStringFactory"
    )
    @Transactional
    public void onProductUpdated(String payload) {
        try {
            String json = (payload != null && !payload.isEmpty() && payload.charAt(0) == '"')
                    ? om.readValue(payload, String.class)
                    : payload;

            ProductUpdateEvent evt = om.readValue(json, ProductUpdateEvent.class);

            String productId = evt.getProductId();
            if (productId == null || productId.isBlank()) {
                log.warn("[INV-UPDATE] productId rỗng trong payload: {}", payload);
                return;
            }
            int newQty = Math.max(0, evt.getQuantity());

            Inventory inv = inventoryRepository.findByProductId(productId)
                    .orElseGet(() -> Inventory.builder().productId(productId).quantity(0).build());

            int old = inv.getQuantity();
            inv.setQuantity(newQty);
            inventoryRepository.save(inv);

            redis.opsForValue().set(stockKey(productId), String.valueOf(newQty));

            log.info("[INV-UPDATE] Sync quantity productId={} | {} -> {} (Redis OK)", productId, old, newQty);
        } catch (Exception e) {
            log.error("[INV-UPDATE] Cannot handle payload: {}", payload, e);
        }
    }

    // ========= product-deleted => xóa inventory + key Redis =========
    @KafkaListener(
            topics = "product-deleted-topic",
            groupId = "inventory-sync-group",
            containerFactory = "productCreatedStringFactory"
    )
    @Transactional
    public void onProductDeleted(String payload) {
        try {
            String json = (payload != null && !payload.isEmpty() && payload.charAt(0) == '"')
                    ? om.readValue(payload, String.class)
                    : payload;

            // Payload xóa hiện đang tái dụng ProductCreatedEvent (chỉ cần id)
            ProductCreatedEvent evt = om.readValue(json, ProductCreatedEvent.class);

            String productId = evt.getProductId();
            if (productId == null || productId.isBlank()) {
                log.warn("[INV-DELETE] productId rỗng trong payload: {}", payload);
                return;
            }

            inventoryRepository.findByProductId(productId).ifPresent(inventoryRepository::delete);
            redis.delete(stockKey(productId));

            log.info("[INV-DELETE] Deleted inventory & Redis for productId={}", productId);
        } catch (Exception e) {
            log.error("[INV-DELETE] Cannot handle payload: {}", payload, e);
            throw new RuntimeException(e);
        }
    }
}
