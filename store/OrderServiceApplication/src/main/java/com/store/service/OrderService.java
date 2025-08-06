package com.store.service;

import com.store.exception.InsufficientStockException;
import com.store.model.Order;
import com.store.repository.OrderRepository;
import com.store.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final OrderKafkaProducer orderKafkaProducer;
    private final InventoryClient inventoryClient;

    public Order createOrder(OrderRequest request) {
        logger.info("============= CREATE ORDER =============");
        int requestedQuantity = request.getQuantity();

        // Lấy số lượng tồn kho từ inventory-service
        int availableQuantity = inventoryClient.getAvailableQuantity(request.getProductId());


        if (requestedQuantity > availableQuantity) {
            // Ném Exception mới với thông báo cụ thể
            throw new InsufficientStockException("Số lượng yêu cầu vượt quá tồn kho!");
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        // Lưu đơn hàng vào DB
        Order savedOrder = orderRepository.save(order);
        logger.info("Đã lưu Order với ID: {}", savedOrder.getId());

        // Gửi sự kiện Kafka
        orderKafkaProducer.sendOrderCreatedEvent(savedOrder);
        logger.info("Đã gửi sự kiện Kafka cho Order ID: {}", savedOrder.getId());

        return savedOrder;
    }

    public void updateOrderStatus(String id, String status) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(status);
        orderRepository.save(order);
    }


}