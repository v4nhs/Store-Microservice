package com.store.service;

import com.store.dto.OrderCreated;
import com.store.dto.ReleaseStock;
import com.store.dto.StockRejected;
import com.store.dto.StockReserved;
import com.store.model.Inventory;
import com.store.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySagaListener {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "order-created",
            groupId = "inventory-group",
            containerFactory = "orderCreatedKafkaListenerFactory")
    @Transactional
    public void onOrderCreated(OrderCreated evt) {
        log.info("[INV] Received order-created: {}", evt);

        // 1) Trừ tồn NGUYÊN TỬ: chỉ trừ khi đủ hàng
        int updated = inventoryRepository.reserve(evt.getProductId(), evt.getQuantity());

        if (updated == 1) {
            // 2) Đã trừ thành công -> phát stock-reserved
            StockReserved reserved = StockReserved.builder()
                    .orderId(evt.getOrderId())
                    .productId(evt.getProductId())
                    .quantity(evt.getQuantity())
                    .build();

            log.info("[INV] Sending stock-reserved: {}", reserved);
            kafkaTemplate.send("stock-reserved", reserved);
        } else {
            // 3) Không đủ tồn -> phát stock-rejected (kèm available/requested để debug)
            int available = inventoryRepository.findByProductId(evt.getProductId())
                    .map(Inventory::getQuantity).orElse(0);
            int requested = evt.getQuantity();

            StockRejected rejected = StockRejected.builder()
                    .orderId(evt.getOrderId())
                    .productId(evt.getProductId())
                    .requested(requested)
                    .reason("INSUFFICIENT_STOCK(" + available + "/" + requested + ")")
                    .build();

            log.info("[INV] Sending stock-rejected: {}", rejected);
            kafkaTemplate.send("stock-rejected", rejected);
        }
    }

    @KafkaListener(
            topics = "release-stock",
            groupId = "inventory-group",
            containerFactory = "releaseStockKafkaListenerFactory")
    @Transactional
    public void onReleaseStock(ReleaseStock evt) {
        log.info("[INV] ReleaseStock: {}", evt);
        // Cộng trả tồn NGUYÊN TỬ
        inventoryRepository.release(evt.getProductId(), evt.getQuantity());
        log.info("[INV] Released {} for productId={}", evt.getQuantity(), evt.getProductId());
    }
}
