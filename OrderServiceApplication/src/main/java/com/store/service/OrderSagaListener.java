package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.StockRejected;
import com.store.dto.StockReserved;
import com.store.model.Order;
import com.store.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaListener {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "stock-reserved",
            groupId = "order-group",
            containerFactory = "stockReservedKafkaListenerFactory"
    )
    @Transactional
    public void onStockReserved(StockReserved evt) {
        log.info("StockReserved: {}", evt);
        Order o = orderRepository.findById(evt.getOrderId()).orElseThrow();
        if (!"CONFIRMED".equals(o.getStatus())) {
            o.setStatus("CONFIRMED");
            orderRepository.save(o);

            OrderDTO confirmed = OrderDTO.builder()
                    .orderId(o.getId())
                    .userId(o.getUserId())
                    .productId(o.getProductId())
                    .quantity(o.getQuantity())
                    .status("CONFIRMED")
                    .build();

            kafkaTemplate.send("order-confirmed", confirmed);
            log.info("Published 'order-confirmed' for {}", o.getId());
        }
    }

    @KafkaListener(
            topics = "stock-rejected",
            groupId = "order-group",
            containerFactory = "stockRejectedKafkaListenerFactory"
    )
    @Transactional
    public void onStockRejected(StockRejected evt) {
        log.info("StockRejected: {}", evt);
        Order o = orderRepository.findById(evt.getOrderId()).orElseThrow();
        if (!"CANCELLED".equals(o.getStatus())) {
            o.setStatus("CANCELLED");
            orderRepository.save(o);

            OrderDTO cancelled = OrderDTO.builder()
                    .orderId(o.getId())
                    .userId(o.getUserId())
                    .productId(o.getProductId())
                    .quantity(o.getQuantity())
                    .status("CANCELLED")
                    .build();

            kafkaTemplate.send("order-cancelled", cancelled);
            log.info("Published 'order-cancelled' for {}", o.getId());
        }
    }
}
