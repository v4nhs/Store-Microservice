package com.store.service;

import com.store.dto.UserDto;
import com.store.model.Order;
import com.store.model.OrderEvent;
import com.store.model.OrderStatus;
import com.store.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaProducer kafkaProducer;
    private final UserService userService;

    public Order createOrder(Order order) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserDto user = userService.getUserById(userId);

        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        OrderEvent event = new OrderEvent(
                savedOrder.getId(),
                user.getEmail(),
                "Đơn hàng của bạn đã được đặt thành công"
        );
        kafkaProducer.sendOrderNotification(event);

        return savedOrder;
    }
}