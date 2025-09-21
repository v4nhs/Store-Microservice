package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.request.OrderCreateRequest;
import com.store.dto.request.OrderItemRequest;
import com.store.event.OrderCreated;
import com.store.dto.ProductDTO;
import com.store.model.Order;
import com.store.model.OrderItem;
import com.store.model.OrderItemStatus;
import com.store.model.OrderStatus;
import com.store.repository.OrderRepository;
import com.store.dto.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    /* ========================= Helpers ========================= */
    private ProductDTO getProduct(String productId) {
        return restTemplate.getForObject(
                "http://product-service/api/products/" + productId,
                ProductDTO.class);
    }

    private Map<String, Object> getProductAsMap(String productId) {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "http://product-service/api/products/" + productId,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        return resp.getBody();
    }

    private boolean hasEnoughFromDto(ProductDTO p, String size, int need) {
        try {
            Object sizesObj = p.getSizes();
            if (sizesObj == null) return false;

            if (sizesObj instanceof java.util.Collection<?> sizes) {
                for (Object obj : sizes) {
                    if (obj == null) continue;
                    try {
                        var c = obj.getClass();
                        var getSize = c.getMethod("getSize");
                        var getQty = c.getMethod("getQuantity");
                        String sName = String.valueOf(getSize.invoke(obj));
                        Integer sQty = (Integer) getQty.invoke(obj);
                        if (sName != null && sName.equalsIgnoreCase(size)) {
                            return sQty != null && sQty >= need;
                        }
                    } catch (Exception ignore) { /* bỏ qua phần tử không khớp */ }
                }
                return false;
            }

            if (sizesObj instanceof String s) {
                String[] tokens = s.split("[,;|]");
                for (String raw : tokens) {
                    if (raw == null) continue;
                    String t = raw.trim();
                    if (t.isEmpty()) continue;

                    String name = t;
                    int qty = 0;
                    int idx = t.indexOf(':');
                    if (idx >= 0) {
                        name = t.substring(0, idx).trim();
                        try {
                            qty = Integer.parseInt(t.substring(idx + 1).trim());
                            if (qty < 0) qty = 0;
                        } catch (NumberFormatException ignore) {
                            qty = 0;
                        }
                    }
                    if (name.equalsIgnoreCase(size)) {
                        return qty >= need;
                    }
                }
                return false;
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasEnoughFromMap(Map<String, Object> product, String size, int need) {
        if (product == null) return false;
        Object sizesObj = product.get("sizes");
        if (!(sizesObj instanceof Collection<?> sizes)) return false;
        for (Object o : sizes) {
            if (o instanceof Map<?, ?> m) {
                Object sName = m.get("size");
                Object sQty = m.get("quantity");
                if (sName != null && size.equalsIgnoreCase(String.valueOf(sName))) {
                    try {
                        int qty = Integer.parseInt(String.valueOf(sQty));
                        return qty >= need;
                    } catch (Exception ignore) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private void assertSizeInStock(String productId, String size, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity phải > 0");

        ProductDTO p = getProduct(productId);
        if (p == null) throw new IllegalStateException("Không tìm thấy sản phẩm: " + productId);

        boolean ok = hasEnoughFromDto(p, size, quantity);
        if (!ok) {
            // fallback đọc Map (trong trường hợp DTO không có size/quantity)
            Map<String, Object> map = getProductAsMap(productId);
            ok = hasEnoughFromMap(map, size, quantity);
        }

        if (!ok) {
            throw new IllegalStateException("Không đủ tồn size '" + size + "' cho productId=" + productId
                    + " (yêu cầu " + quantity + ")");
        }
    }

    /* ========================= Create Order ========================= */
    private static final String UUID_RE =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    @Transactional
    public Order createOrder(String userId, List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Danh sách items trống");
        }

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (var it : items) {
            ProductDTO p = getProduct(it.getProductId());
            if (p == null || p.getPrice() == null || p.getPrice().signum() <= 0) {
                throw new IllegalStateException("Giá không hợp lệ cho productId=" + it.getProductId());
            }

            assertSizeInStock(it.getProductId(), it.getSize(), it.getQuantity());

            BigDecimal unit = p.getPrice();
            BigDecimal line = unit.multiply(BigDecimal.valueOf(it.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .productId(it.getProductId())
                    .size(it.getSize())
                    .quantity(it.getQuantity())
                    .unitPrice(unit)
                    .lineAmount(line)
                    .itemStatus(OrderItemStatus.PENDING)
                    .build();

            order.addItem(item);
            total = total.add(line);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        publishOrderCreatedAfterCommit(saved);
        return saved;
    }

    /* ========================= Publish event ========================= */

    private void publishOrderCreatedAfterCommit(Order saved) {
        OrderCreated evt = OrderCreated.builder()
                .orderId(saved.getId())
                .userId(saved.getUserId())
                .totalAmount(saved.getTotalAmount())
                .items(saved.getItems().stream().map(i ->
                        new OrderCreated.Item(
                                i.getProductId(),
                                i.getSize(),
                                i.getQuantity(),
                                i.getUnitPrice()
                        )
                ).collect(Collectors.toList()))
                .build();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send("order-created", evt);
                log.info("Published 'order-created' (items={}) for {}", saved.getItems().size(), saved.getId());
            }
        });
    }

    /* ========================= Queries ========================= */

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id=" + id));
    }

    public List<Order> getAllByUser(String userId) {
        return orderRepository.findByUserIdOrderByIdDesc(userId);
    }

    public Order getByIdForUser(String orderId, String userId) {
        return orderRepository.findById(orderId)
                .filter(o -> userId.equals(o.getUserId()))
                .orElse(null);
    }

    public OrderDTO toDto(Order o) {
        if (o == null) return null;

        java.util.List<com.store.dto.OrderItemDTO> itemDTOs =
                (o.getItems() == null ? java.util.List.of() :
                        o.getItems().stream().filter(java.util.Objects::nonNull).map(i -> {
                            java.math.BigDecimal unit = i.getUnitPrice() == null ? java.math.BigDecimal.ZERO : i.getUnitPrice();
                            int qty = i.getQuantity() == null ? 0 : i.getQuantity();
                            java.math.BigDecimal line = unit.multiply(java.math.BigDecimal.valueOf(qty));
                            return com.store.dto.OrderItemDTO.builder()
                                    .productId(i.getProductId())
                                    .size(i.getSize())
                                    .quantity(qty)
                                    .unitPrice(unit)
                                    .lineAmount(line)
                                    .build();
                        }).toList());

        java.math.BigDecimal total = o.getTotalAmount();
        if (total == null) {
            total = itemDTOs.stream()
                    .map(it -> it.getLineAmount() == null ? java.math.BigDecimal.ZERO : it.getLineAmount())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        }

        return com.store.dto.OrderDTO.builder()
                .orderId(o.getId())
                .userId(o.getUserId())
                .status(o.getStatus() == null ? null : o.getStatus().name())
                .totalAmount(total)
                .items(itemDTOs)
                .build();
    }

    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrderDtos() {
        return orderRepository.findAllWithItems().stream().map(this::toDto).toList();
    }
}
