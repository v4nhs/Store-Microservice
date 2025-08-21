package com.store.service;

import com.store.dto.request.OrderCreateRequest;
import com.store.event.OrderCreated;
import com.store.dto.ProductDTO;
import com.store.dto.UserDTO;
import com.store.model.Order;
import com.store.model.OrderItem;
import com.store.model.OrderItemStatus;
import com.store.model.OrderStatus;
import com.store.repository.OrderRepository;
import com.store.dto.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderRequest request) {
        log.info("============= CREATE ORDER =============");
        List<Order> saved = createOrder(List.of(request));
        return saved.get(0);
    }
    @Transactional
    public Order createOrderMulti(OrderCreateRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty())
            throw new IllegalArgumentException("Danh sách items trống");

        Order order = Order.builder()
                .userId(req.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (var it : req.getItems()) {
            ProductDTO p = restTemplate.getForObject(
                    "http://product-service/api/products/" + it.getProductId(),
                    ProductDTO.class);

            if (p == null || p.getPrice() == null || p.getPrice().signum() <= 0)
                throw new IllegalStateException("Giá không hợp lệ: " + it.getProductId());

            BigDecimal unit = p.getPrice();
            BigDecimal line = unit.multiply(BigDecimal.valueOf(it.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .productId(it.getProductId())
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

        for (OrderRequest req : requests) {
            if (req.getQuantity() == null || req.getQuantity() < 1) {
                throw new IllegalArgumentException("quantity phải >= 1 (productId=" + req.getProductId() + ")");
            }

            ProductDTO p = restTemplate.getForObject(
                    "http://product-service/api/products/" + req.getProductId(),
                    ProductDTO.class);
            if (p == null || p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Không lấy được giá hợp lệ cho productId=" + req.getProductId());
            }

            BigDecimal unit = p.getPrice();
            BigDecimal line = unit.multiply(BigDecimal.valueOf(req.getQuantity()));

            // Tạo Order + 1 OrderItem
            Order o = Order.builder()
                    .userId(req.getUserId())
                    .status(OrderStatus.PENDING)
                    .totalAmount(line)
                    .build();

            OrderItem item = OrderItem.builder()
                    .productId(req.getProductId())
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

    /** Dùng chung: publish 'order-created' (multi-items) sau commit */
    private void publishOrderCreatedAfterCommit(Order saved) {
        OrderCreated evt = OrderCreated.builder()
                .orderId(saved.getId())
                .userId(saved.getUserId())
                .totalAmount(saved.getTotalAmount())
                .items(saved.getItems().stream().map(i ->
                                new OrderCreated.Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                        .toList())
                .build();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                kafkaTemplate.send("order-created", evt);
                log.info("Published 'order-created' (items={}) for {}", saved.getItems().size(), saved.getId());
            }
        });
    }
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id=" + id));
    }
}