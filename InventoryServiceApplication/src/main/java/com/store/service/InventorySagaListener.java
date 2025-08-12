package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.OrderCreated;
import com.store.dto.ReleaseStock;
import com.store.dto.StockRejected;
import com.store.dto.StockReserved;
import com.store.model.OutboxEvent;
import com.store.repository.OutboxRepository;
import com.store.repository.InventoryRepository;
import com.store.model.Inventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class InventorySagaListener {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final OutboxRepository outboxRepo;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper om = new ObjectMapper();

    public InventorySagaListener(
            StringRedisTemplate redis,
            @Qualifier("reserveScript") DefaultRedisScript<Long> reserveScript,
            @Qualifier("releaseScript") DefaultRedisScript<Long> releaseScript,
            OutboxRepository outboxRepo,
            InventoryRepository inventoryRepository
    ) {
        this.redis = redis;
        this.reserveScript = reserveScript;
        this.releaseScript = releaseScript;
        this.outboxRepo = outboxRepo;
        this.inventoryRepository = inventoryRepository;
    }

    private String stockKey(String productId){ return "stock:" + productId; }
    private String seenKey(String orderId){ return "order:seen:" + orderId; }

    // Khởi tạo Redis từ DB nếu key chưa có
    private void ensureRedisStockKey(String productId) {
        String key = stockKey(productId);
        String val = redis.opsForValue().get(key);
        if (val == null) {
            int dbQty = inventoryRepository.findByProductId(productId)
                    .map(Inventory::getQuantity)
                    .orElse(0);
            redis.opsForValue().setIfAbsent(key, String.valueOf(dbQty));
            log.info("[INV] Init Redis {} = {} (from DB)", key, dbQty);
        }
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "inventory-group",
            containerFactory = "orderCreatedKafkaListenerFactory")
    @Transactional
    public void onOrderCreated(OrderCreated evt) {
        if (evt.getQuantity() <= 0) {
            log.warn("[INV] Bỏ qua order {}, quantity không hợp lệ: {}", evt.getOrderId(), evt.getQuantity());
            return;
        }
        log.info("[INV] Received order-created: {}", evt);

        ensureRedisStockKey(evt.getProductId());

        var keys = List.of(stockKey(evt.getProductId()), seenKey(evt.getOrderId()));
        var args = List.of(evt.getOrderId(), String.valueOf(evt.getQuantity()), "600"); // TTL idempotency 10p

        Long res = redis.execute(reserveScript, keys, args.toArray()); // 1=OK, 0=insufficient, 2=duplicate
        try {
            if (res != null && res == 1L) {
                // 1) STOCK_RESERVED (để order-service consume nếu có)
                var reserved = StockReserved.builder()
                        .orderId(evt.getOrderId())
                        .productId(evt.getProductId())
                        .quantity(evt.getQuantity())
                        .build();
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateType("order")
                        .aggregateId(evt.getOrderId())
                        .eventType("STOCK_RESERVED")
                        .payload(om.writeValueAsString(reserved))
                        .status("NEW")
                        .build());

                // 2) PRODUCT_STOCK_DECREASED (cho product-service trừ DB product)
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateType("product")
                        .aggregateId(evt.getProductId())
                        .eventType("PRODUCT_STOCK_DECREASED")
                        .payload(om.writeValueAsString(Map.of(
                                "productId", evt.getProductId(),
                                "quantity", evt.getQuantity()
                        )))
                        .status("NEW")
                        .build());

                // 3) ORDER_CONFIRMED (để noti-service gửi mail, không cần sửa order-service)
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateType("order")
                        .aggregateId(evt.getOrderId())
                        .eventType("ORDER_CONFIRMED")
                        .payload(om.writeValueAsString(Map.of(
                                "orderId", evt.getOrderId(),
                                "userId", evt.getUserId(),
                                "productId", evt.getProductId(),
                                "quantity", evt.getQuantity(),
                                "status", "CONFIRMED"
                        )))
                        .status("NEW")
                        .build());

                log.info("[INV] Reserved OK by Lua for productId={}, qty={}", evt.getProductId(), evt.getQuantity());

            } else if (res != null && res == 2L) {
                log.info("[INV] Skip duplicate orderId={} (idempotent)", evt.getOrderId());

            } else {
                var rejected = StockRejected.builder()
                        .orderId(evt.getOrderId())
                        .productId(evt.getProductId())
                        .requested(evt.getQuantity())
                        .reason("INSUFFICIENT_STOCK")
                        .build();
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateType("order")
                        .aggregateId(evt.getOrderId())
                        .eventType("STOCK_REJECTED")
                        .payload(om.writeValueAsString(rejected))
                        .status("NEW")
                        .build());

                // 4) ORDER_CANCELLED (để noti-service gửi mail)
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateType("order")
                        .aggregateId(evt.getOrderId())
                        .eventType("ORDER_CANCELLED")
                        .payload(om.writeValueAsString(Map.of(
                                "orderId", evt.getOrderId(),
                                "userId", evt.getUserId(),
                                "productId", evt.getProductId(),
                                "quantity", evt.getQuantity(),
                                "status", "CANCELLED"
                        )))
                        .status("NEW")
                        .build());

                log.info("[INV] Insufficient stock for productId={}, need={}", evt.getProductId(), evt.getQuantity());
            }
        } catch (Exception e) {
            log.error("[INV] onOrderCreated failed", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
            topics = "release-stock",
            groupId = "inventory-group",
            containerFactory = "releaseStockKafkaListenerFactory")
    @Transactional
    public void onReleaseStock(ReleaseStock evt) {
        log.info("[INV] ReleaseStock: {}", evt);

        ensureRedisStockKey(evt.getProductId());

        var keys = List.of(stockKey(evt.getProductId()), seenKey(evt.getOrderId()));
        var args = List.of(evt.getOrderId(), String.valueOf(evt.getQuantity()));
        redis.execute(releaseScript, keys, args.toArray());

        try {
            outboxRepo.save(OutboxEvent.builder()
                    .aggregateType("order")
                    .aggregateId(evt.getOrderId())
                    .eventType("STOCK_RELEASED")
                    .payload(om.writeValueAsString(Map.of(
                            "orderId", evt.getOrderId(),
                            "productId", evt.getProductId(),
                            "quantity", evt.getQuantity()
                    )))
                    .status("NEW")
                    .build());
        } catch (Exception e) {
            log.error("[INV] onReleaseStock JSON failed", e);
            throw new RuntimeException(e);
        }

        log.info("[INV] Released {} for productId={}", evt.getQuantity(), evt.getProductId());
    }
}
