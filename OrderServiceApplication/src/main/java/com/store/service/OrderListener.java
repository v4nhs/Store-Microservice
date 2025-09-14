package com.store.service;

import com.store.dto.OrderDTO;
import com.store.event.ReleaseStock;
import com.store.event.StockRejected;
import com.store.event.StockReserved;
import com.store.model.OrderItemStatus;
import com.store.model.OrderStatus;
import com.store.repository.OrderItemRepository;
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
public class OrderListener {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "stock-reserved",
            containerFactory = "stockReservedKafkaListenerFactory"
    )
    @Transactional
    public void onStockReserved(StockReserved evt) {
        var o = orderRepository.findById(evt.getOrderId()).orElse(null);
        if (o == null) {
            kafkaTemplate.send("release-stock",
                    ReleaseStock.builder()
                            .orderId(evt.getOrderId())
                            .productId(evt.getProductId())
                            .size(evt.getSize())
                            .quantity(evt.getQuantity())
                            .build());
            return;
        }

        orderItemRepository.findByOrderIdAndProductIdAndSize(o.getId(), evt.getProductId(), evt.getSize())
                .ifPresent(i -> i.setItemStatus(OrderItemStatus.RESERVED));

        boolean allReserved = o.getItems().stream()
                .allMatch(i -> i.getItemStatus() == OrderItemStatus.RESERVED);

        if (allReserved && o.getStatus() != OrderStatus.CONFIRMED) {
            o.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(o);
            kafkaTemplate.send("order-confirmed",
                    OrderDTO.builder().orderId(o.getId()).userId(o.getUserId()).status(o.getStatus().name()).build());
        }
    }

    @KafkaListener(topics = "stock-rejected", containerFactory = "stockRejectedKafkaListenerFactory")
    @Transactional
    public void onStockRejected(StockRejected evt) {
        var o = orderRepository.findById(evt.getOrderId()).orElse(null);
        if (o == null) return;

        orderItemRepository.findByOrderIdAndProductIdAndSize(o.getId(), evt.getProductId(), evt.getSize())
                .ifPresent(i -> i.setItemStatus(OrderItemStatus.REJECTED));

        if (o.getStatus() != OrderStatus.CANCELLED) {
            o.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(o);

            o.getItems().stream()
                    .filter(i -> i.getItemStatus() == OrderItemStatus.RESERVED)
                    .forEach(i -> kafkaTemplate.send("release-stock",
                            ReleaseStock.builder()
                                    .orderId(o.getId())
                                    .productId(i.getProductId())
                                    .size(i.getSize())
                                    .quantity(i.getQuantity())
                                    .build()));

            kafkaTemplate.send("order-cancelled",
                    OrderDTO.builder().orderId(o.getId()).userId(o.getUserId()).status(o.getStatus().name()).build());
        }
    }
}
