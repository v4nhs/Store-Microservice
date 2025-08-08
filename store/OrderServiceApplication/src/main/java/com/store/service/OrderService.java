package com.store.service;

import com.store.model.Order;
import com.store.repository.OrderRepository;
import com.store.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderKafkaProducer orderKafkaProducer;
    private final InventoryClient inventoryClient;
    private final RestTemplate restTemplate;

    public Order createOrder(OrderRequest request) {
        logger.info("============= CREATE ORDER =============");

        boolean reserved = inventoryClient.reserveStock(request.getProductId(), request.getQuantity());

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());

        if (reserved) {
            order.setStatus("CONFIRMED");
            logger.info("Order được xác nhận: sản phẩm {} số lượng {}", request.getProductId(), request.getQuantity());
        } else {
            order.setStatus("REJECT");
            logger.warn("Order bị từ chối: sản phẩm {} không đủ hàng", request.getProductId());
        }

        Order savedOrder = orderRepository.save(order);
        logger.info("Đã lưu Order với ID: {}", savedOrder.getId());

        // Chỉ gửi Kafka khi order được xác nhận
        if ("CONFIRMED".equals(savedOrder.getStatus())) {
            orderKafkaProducer.sendOrderCreatedEvent(savedOrder);
            logger.info("Đã gửi sự kiện Kafka cho Order ID: {}", savedOrder.getId());
        }

        return savedOrder;
    }

    public Order getOrderById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }
    public boolean decreaseStock(String productId, int quantity) {
        String url = "http://inventory-service/api/inventory/decrease?productId=" + productId + "&quantity=" + quantity;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.out.println("Error calling decreaseStock: " + e.getMessage());
            return false;
        }
    }
}