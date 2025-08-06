package com.store.service;

import com.store.dto.OrderItemDTO;
import com.store.model.Inventory;
import com.store.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService{

    private final InventoryRepository inventoryRepository;

    public int getAvailableQuantity(String productId) {
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    public boolean checkStock(List<OrderItemDTO> items) {
        for (OrderItemDTO item : items) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).orElse(null);
            if (inventory == null || inventory.getQuantity() < item.getQuantity()) {
                return false;
            }
        }
        for (OrderItemDTO item : items) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).orElse(null);
            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
                inventoryRepository.save(inventory);
            }
        }
        return true;
    }
}