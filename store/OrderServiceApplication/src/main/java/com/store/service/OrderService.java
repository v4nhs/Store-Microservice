package com.store.service;

import com.store.dto.OrderDto;
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

    public Order createOrder(OrderRequest request) {
        Order order = new Order();
        logger.info("=============================");
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(String.valueOf(request.getQuantity()));
        order.setStatus("PENDING");

        orderRepository.save(order);
        logger.info("Đã gọi lệnh save() cho order: {}", order.getId());

        orderKafkaProducer.sendOrderCreatedEvent(order);
        logger.info("Đã gọi lệnh gửi sự kiện Kafka.");
        return orderRepository.save(order);
    }

    public void updateOrderStatus(String id, String status) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(status);
        orderRepository.save(order);
    }
}