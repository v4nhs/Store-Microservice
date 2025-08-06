package com.store.service;

import com.store.dto.ProductCreatedEvent;
import com.store.model.Inventory;
import com.store.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class InventoryKafkaConsumer {
    private final InventoryRepository inventoryRepository;

    @KafkaListener(topics = "product-created-topic", groupId = "inventory-group")
    public void handleProductCreated(ProductCreatedEvent event) {
        Inventory inventory = new Inventory();
        inventory.setProductId(event.getProductId());
        inventory.setQuantity(event.getQuantity());
        inventoryRepository.save(inventory);
    }
}