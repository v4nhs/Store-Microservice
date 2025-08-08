package com.store.service;

import com.store.dto.OrderItemDTO;
import com.store.model.Inventory;
import com.store.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final RestTemplate restTemplate; // thêm RestTemplate

    public int getAvailableQuantity(String productId) {
        System.out.println("[InventoryService] Kiểm tra số lượng tồn cho productId: " + productId);
        return inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElse(0);
    }

    public boolean checkStock(List<OrderItemDTO> items) {
        System.out.println("[InventoryService] Bắt đầu kiểm tra tồn kho cho đơn hàng...");
        for (OrderItemDTO item : items) {
            System.out.println("  -> Check productId: " + item.getProductId() + ", requested quantity: " + item.getQuantity());
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).orElse(null);
            System.out.println("     Inventory found: " + inventory);

            if (inventory == null || inventory.getQuantity() < item.getQuantity()) {
                System.out.println("     ❌ Không đủ hàng cho productId: " + item.getProductId());
                return false;
            }
        }

        System.out.println("[InventoryService] Đủ hàng, tiến hành trừ số lượng...");
        for (OrderItemDTO item : items) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId()).orElse(null);
            if (inventory != null) {
                int oldQty = inventory.getQuantity();
                inventory.setQuantity(oldQty - item.getQuantity());
                inventoryRepository.save(inventory);

                // Đồng bộ sang Product Service
                syncProductQuantity(item.getProductId(), inventory.getQuantity());

                System.out.println("  ✅ Đã cập nhật tồn kho productId: " + item.getProductId() +
                        " | Old Qty: " + oldQty + " -> New Qty: " + inventory.getQuantity());
            }
        }
        return true;
    }

    public boolean decreaseStock(String productId, int quantity) {
        System.out.println("[InventoryService] Yêu cầu giảm " + quantity + " cho productId: " + productId);
        Optional<Inventory> productOpt = inventoryRepository.findById(productId);

        if (productOpt.isPresent()) {
            Inventory product = productOpt.get();
            System.out.println("  -> Tồn kho hiện tại: " + product.getQuantity());

            if (product.getQuantity() >= quantity) {
                product.setQuantity(product.getQuantity() - quantity);
                inventoryRepository.save(product);
                System.out.println("  ✅ Giảm số lượng thành công! New Qty: " + product.getQuantity());

                // Đồng bộ sang Product Service
                syncProductQuantity(productId, product.getQuantity());

                return true;
            } else {
                System.out.println("  ❌ Không đủ tồn kho!");
            }
        } else {
            System.out.println("  ❌ Không tìm thấy productId: " + productId);
        }
        return false;
    }

    private void syncProductQuantity(String productId, int updatedQuantity) {
        try {
            String url = "http://product-service/api/products/" + productId + "/quantity?quantity=" + updatedQuantity;
            System.out.println("  🌐 Gọi Product Service để đồng bộ quantity: " + updatedQuantity);
            restTemplate.put(url, null);
            System.out.println("  ✅ Đồng bộ Product Service thành công!");
        } catch (Exception e) {
            System.out.println("  ❌ Lỗi khi đồng bộ sang Product Service: " + e.getMessage());
        }
    }
}
