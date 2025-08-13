package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.ProductCreatedEvent;
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
            ProductCreatedEvent evt = om.readValue(payload, ProductCreatedEvent.class);

            String productId = evt.getProductId();
            int initialQty = Math.max(0, evt.getQuantity());

            boolean existed = inventoryRepository.findByProductId(productId).isPresent();
            if (existed) {
                log.info("[INV-INIT] Inventory existed for productId={}, skip insert", productId);
                return;
            }

            Inventory created = Inventory.builder()
                    .productId(productId)
                    .quantity(initialQty)
                    .build();
            inventoryRepository.save(created);

            log.info("[INV-INIT] Inserted inventory productId={}, quantity={}", productId, initialQty);

        } catch (Exception e) {
            log.error("[INV-INIT] Cannot handle product-created payload: {}", payload, e);
            throw new RuntimeException(e);
        }
    }
}
