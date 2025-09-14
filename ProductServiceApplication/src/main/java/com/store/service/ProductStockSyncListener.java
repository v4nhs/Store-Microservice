package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.StockReserved;
import com.store.dto.ReleaseStock;
import com.store.model.Product;
import com.store.model.ProductSize;
import com.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductStockSyncListener {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();

    // ===== Idempotency keys theo từng ITEM SIZE =====
    private String reservedKey(String orderId, String productId, String size) {
        return "proj:product:reserved:" + orderId + "#" + productId + "#" + size;
    }
    private String releasedKey(String orderId, String productId, String size) {
        return "proj:product:released:" + orderId + "#" + productId + "#" + size;
    }

    private String unwrapIfQuoted(String payload) {
        try {
            if (payload != null && !payload.isEmpty() && payload.charAt(0) == '"') {
                return om.readValue(payload, String.class);
            }
        } catch (Exception ignore) {}
        return payload;
    }

    // ================== stock-reserved => TRỪ size.quantity ==================
    @KafkaListener(topics = "stock-reserved", groupId = "product-projection-group")
    @Transactional
    public void onStockReserved(String payload) {
        String raw = unwrapIfQuoted(payload);
        try {
            StockReserved evt = om.readValue(raw, StockReserved.class);

            String key = reservedKey(evt.getOrderId(), evt.getProductId(), evt.getSize());
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                log.info("[PROD] Skip duplicate RESERVED for {}#{}#{}", evt.getOrderId(), evt.getProductId(), evt.getSize());
                return;
            }

            Optional<Product> opt = productRepository.findById(evt.getProductId());
            if (opt.isEmpty()) {
                redis.delete(key);
                log.warn("[PROD] Product not found for reserved: pid={}, size={}, orderId={}",
                        evt.getProductId(), evt.getSize(), evt.getOrderId());
                return;
            }

            Product p = opt.get();
            ProductSize ps = p.getSizes().stream()
                    .filter(s -> s.getSize().equalsIgnoreCase(evt.getSize()))
                    .findFirst()
                    .orElse(null);

            if (ps == null) {
                // nếu không có size tương ứng → không trừ, cho phép retry sau khi dữ liệu được sửa
                redis.delete(key);
                log.warn("[PROD] Size not found for reserved: pid={}, size={}, orderId={}",
                        evt.getProductId(), evt.getSize(), evt.getOrderId());
                return;
            }

            int left = ps.getQuantity() - Math.max(0, evt.getQuantity());
            if (left < 0) left = 0;
            ps.setQuantity(left);

            p.recalcQuantityFromSizes();

            productRepository.save(p);
            log.info("[PROD] RESERVED OK pid={}, size={}, -{} => sizeQty={}, totalQty={}, orderId={}",
                    evt.getProductId(), evt.getSize(), evt.getQuantity(), ps.getQuantity(), p.getQuantity(), evt.getOrderId());
        } catch (Exception e) {
            log.error("[PROD] Cannot handle stock-reserved payload: {}", payload, e);
            // không xóa key để có thể retry
        }
    }

    // ================== release-stock => CỘNG size.quantity ==================
    @KafkaListener(topics = "release-stock", groupId = "product-projection-group")
    @Transactional
    public void onReleaseStock(String payload) {
        String raw = unwrapIfQuoted(payload);
        try {
            ReleaseStock evt = om.readValue(raw, ReleaseStock.class);

            String key = releasedKey(evt.getOrderId(), evt.getProductId(), evt.getSize());
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
            if (Boolean.FALSE.equals(first)) {
                log.info("[PROD] Skip duplicate RELEASE for {}#{}#{}", evt.getOrderId(), evt.getProductId(), evt.getSize());
                return;
            }

            Optional<Product> opt = productRepository.findById(evt.getProductId());
            if (opt.isEmpty()) {
                redis.delete(key);
                log.warn("[PROD] Product not found for release: pid={}, size={}, orderId={}",
                        evt.getProductId(), evt.getSize(), evt.getOrderId());
                return;
            }

            Product p = opt.get();
            ProductSize ps = p.getSizes().stream()
                    .filter(s -> s.getSize().equalsIgnoreCase(evt.getSize()))
                    .findFirst()
                    .orElse(null);

            if (ps == null) {
                redis.delete(key);
                log.warn("[PROD] Size not found for release: pid={}, size={}, orderId={}",
                        evt.getProductId(), evt.getSize(), evt.getOrderId());
                return;
            }

            int next = ps.getQuantity() + Math.max(0, evt.getQuantity());
            ps.setQuantity(next);

            // Tổng product.quantity = SUM(size.quantity)
            p.recalcQuantityFromSizes();

            productRepository.save(p);
            log.info("[PROD] RELEASE OK pid={}, size={}, +{} => sizeQty={}, totalQty={}, orderId={}",
                    evt.getProductId(), evt.getSize(), evt.getQuantity(), ps.getQuantity(), p.getQuantity(), evt.getOrderId());
        } catch (Exception e) {
            log.error("[PROD] Cannot handle release-stock payload: {}", payload, e);
        }
    }
}
