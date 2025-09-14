package com.store.service;

import com.store.dto.request.OrderCreateRequest;
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
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        return resp.getBody();
    }

    private boolean hasEnoughFromDto(ProductDTO p, String size, int need) {
        try {
            Object sizesObj = p.getSizes(); // có thể là List<?> hoặc String
            if (sizesObj == null) return false;

            // Trường hợp DTO trả về COLLECTION (List<ProductSizeDTO> hoặc tương tự)
            if (sizesObj instanceof java.util.Collection<?> sizes) {
                for (Object obj : sizes) {
                    if (obj == null) continue;
                    try {
                        var c = obj.getClass();
                        var getSize = c.getMethod("getSize");
                        var getQty  = c.getMethod("getQuantity");
                        String sName = String.valueOf(getSize.invoke(obj));
                        Integer sQty = (Integer) getQty.invoke(obj);
                        if (sName != null && sName.equalsIgnoreCase(size)) {
                            return sQty != null && sQty >= need;
                        }
                    } catch (Exception ignore) { /* bỏ qua phần tử không khớp */ }
                }
                return false;
            }

            // Trường hợp DTO trả về STRING (CSV): "S:10, M:5" hoặc "S, M, L"
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
                        } catch (NumberFormatException ignore) { qty = 0; }
                    }
                    if (name.equalsIgnoreCase(size)) {
                        return qty >= need;
                    }
                }
                return false;
            }

            // Kiểu khác không hỗ trợ
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasEnoughFromMap(Map<String, Object> product, String size, int need) {
        if (product == null) return false;
        Object sizesObj = product.get("sizes");
        if (!(sizesObj instanceof Collection<?> sizes)) return false;
        for (Object o : sizes) {
            if (o instanceof Map<?,?> m) {
                Object sName = m.get("size");
                Object sQty  = m.get("quantity");
                if (sName != null && size.equalsIgnoreCase(String.valueOf(sName))) {
                    try {
                        int qty = Integer.parseInt(String.valueOf(sQty));
                        return qty >= need;
                    } catch (Exception ignore) { return false; }
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

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderRequest request) {
        log.info("============= CREATE ORDER (single) =============");

        if (request.getQuantity() == null || request.getQuantity() < 1) {
            throw new IllegalArgumentException("quantity phải >= 1 (productId=" + request.getProductId() + ")");
        }

        // 1) Lấy giá & kiểm tra tồn theo size
        ProductDTO p = getProduct(request.getProductId());
        if (p == null || p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Không lấy được giá hợp lệ cho productId=" + request.getProductId());
        }
        assertSizeInStock(request.getProductId(), request.getSize(), request.getQuantity());

        // 2) Tạo order + item (giữ nguyên logic cũ)
        BigDecimal unit = p.getPrice();
        BigDecimal line = unit.multiply(BigDecimal.valueOf(request.getQuantity()));

        Order o = Order.builder()
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(line)
                .build();

        OrderItem item = OrderItem.builder()
                .productId(request.getProductId())
                .size(request.getSize())
                .quantity(request.getQuantity())
                .unitPrice(unit)
                .lineAmount(line)
                .itemStatus(OrderItemStatus.PENDING)
                .build();

        o.addItem(item);

        Order saved = orderRepository.save(o);
        publishOrderCreatedAfterCommit(saved);
        return saved;
    }

    @Transactional
    public Order createOrderMulti(OrderCreateRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty())
            throw new IllegalArgumentException("Danh sách items trống");

        // Gom các productId cần query để giảm số lần call (cơ bản)
        Map<String, ProductDTO> productCache = new HashMap<>();

        Order order = Order.builder()
                .userId(req.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (var it : req.getItems()) {
            if (it.getQuantity() == null || it.getQuantity() < 1) {
                throw new IllegalArgumentException("quantity phải >= 1 (productId=" + it.getProductId() + ")");
            }
            // 1) Lấy giá & kiểm tra tồn theo size
            ProductDTO p = productCache.computeIfAbsent(it.getProductId(), this::getProduct);
            if (p == null || p.getPrice() == null || p.getPrice().signum() <= 0)
                throw new IllegalStateException("Giá không hợp lệ: " + it.getProductId());

            assertSizeInStock(it.getProductId(), it.getSize(), it.getQuantity());

            // 2) Tạo item
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
            grandTotal = grandTotal.add(line);
        }

        order.setTotalAmount(grandTotal);
        Order saved = orderRepository.save(order);

        publishOrderCreatedAfterCommit(saved);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Order> createOrder(List<OrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalStateException("Danh sách trống");
        }

        List<Order> toSave = new ArrayList<>(requests.size());
        Map<String, ProductDTO> productCache = new HashMap<>();

        for (OrderRequest req : requests) {
            if (req.getQuantity() == null || req.getQuantity() < 1) {
                throw new IllegalArgumentException("quantity phải >= 1 (productId=" + req.getProductId() + ")");
            }

            // 1) Lấy giá & kiểm tra tồn theo size
            ProductDTO p = productCache.computeIfAbsent(req.getProductId(), this::getProduct);
            if (p == null || p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Không lấy được giá hợp lệ cho productId=" + req.getProductId());
            }
            assertSizeInStock(req.getProductId(), req.getSize(), req.getQuantity());

            // 2) Build order + item
            BigDecimal unit = p.getPrice();
            BigDecimal line = unit.multiply(BigDecimal.valueOf(req.getQuantity()));

            Order o = Order.builder()
                    .userId(req.getUserId())
                    .status(OrderStatus.PENDING)
                    .totalAmount(line)
                    .build();

            OrderItem item = OrderItem.builder()
                    .productId(req.getProductId())
                    .size(req.getSize())
                    .quantity(req.getQuantity())
                    .unitPrice(unit)
                    .lineAmount(line)
                    .itemStatus(OrderItemStatus.PENDING)
                    .build();

            o.addItem(item);
            toSave.add(o);
        }

        List<Order> saved = orderRepository.saveAll(toSave);
        for (Order o : saved) publishOrderCreatedAfterCommit(o);
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
            @Override public void afterCommit() {
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
}
