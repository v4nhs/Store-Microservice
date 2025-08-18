package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.ProductCreatedEvent;
import com.store.dto.ProductUpdateEvent;
import com.store.model.Inventory;
import com.store.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ObjectMapper om = new ObjectMapper();

    @KafkaListener(
            topics = "product-created-topic",
            groupId = "inventory-init-group",
            containerFactory = "productCreatedStringFactory"
    )
    @Transactional
    public void onProductCreated(String payload) {
        try {
            String json = (payload != null && !payload.isEmpty() && payload.charAt(0) == '"')
                    ? om.readValue(payload, String.class)
                    : payload;

            ProductCreatedEvent evt = om.readValue(payload, ProductCreatedEvent.class);
            String productId = evt.getProductId();
            int initialQty = Math.max(0, evt.getQuantity());

            Inventory inv = inventoryRepository.findByProductId(productId)
                    .orElseGet(() -> Inventory.builder().productId(productId).quantity(0).build());

            int old = inv.getQuantity();
            inv.setQuantity(initialQty);
            inventoryRepository.save(inv);

            log.info("[INV-INIT] Upsert inventory productId={}, {} -> {}", productId, old, initialQty);
        } catch (Exception e) {
            log.error("[INV-INIT] Cannot handle product-created payload: {}", payload, e);
        }
    }
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

            ProductUpdateEvent evt = om.readValue(payload, ProductUpdateEvent.class);
            String productId = evt.getProductId();
            int newQty = Math.max(0, evt.getQuantity());

            Inventory inv = inventoryRepository.findByProductId(productId)
                    .orElseGet(() -> Inventory.builder().productId(productId).quantity(0).build());

            int old = inv.getQuantity();
            inv.setQuantity(newQty);
            inventoryRepository.save(inv);

            log.info("[INV-UPDATE] Sync quantity productId={} | {} -> {}", productId, old, newQty);
        } catch (Exception e) {
            log.error("[INV-UPDATE] Cannot handle payload: {}", payload, e);
        }
    }

    @KafkaListener(
            topics = "product-deleted-topic",
            groupId = "inventory-sync-group",
            containerFactory = "productCreatedStringFactory"
    )
    @Transactional
    public void onProductDeleted(String payload) {
        try {
            ProductCreatedEvent evt = om.readValue(payload, ProductCreatedEvent.class);
            String productId = evt.getProductId();
            inventoryRepository.findByProductId(productId).ifPresent(inventoryRepository::delete);
            log.info("[INV-DELETE] Deleted inventory for productId={}", productId);
        } catch (Exception e) {
            log.error("[INV-DELETE] Cannot handle payload: {}", payload, e);
            throw new RuntimeException(e);
        }
    }
}
