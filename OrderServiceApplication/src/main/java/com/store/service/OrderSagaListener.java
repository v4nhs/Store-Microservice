package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ReleaseStock;
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

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaListener {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
            topics = "stock-reserved",
            containerFactory = "stockReservedKafkaListenerFactory"
    )
    @Transactional
    public void onStockReserved(StockReserved evt) {
        log.info("StockReserved: {}", evt);

        Optional<Order> opt = orderRepository.findById(evt.getOrderId());
        if (opt.isEmpty()) {
            log.warn("Không tìm thấy order {}, gửi release-stock và bỏ qua.", evt.getOrderId());
            ReleaseStock rs = ReleaseStock.builder()
                    .orderId(evt.getOrderId())
                    .productId(evt.getProductId())
                    .quantity(evt.getQuantity())
                    .build();
            kafkaTemplate.send("release-stock", rs);
            return;
        }

        Order o = opt.get();
        String status = o.getStatus();

        if ("CONFIRMED".equals(status)) {
            log.info("Order {} đã CONFIRMED trước đó (idempotent). Bỏ qua.", o.getId());
            return;
        }

        if ("CANCELLED".equals(status)) {
            // Trường hợp out-of-order: đã hủy mà lại nhận reserved -> hoàn kho thêm (Lua release idempotent)
            log.warn("Order {} đang CANCELLED nhưng nhận reserved. Gửi release-stock idempotent.", o.getId());
            ReleaseStock rs = ReleaseStock.builder()
                    .orderId(evt.getOrderId())
                    .productId(evt.getProductId())
                    .quantity(evt.getQuantity())
                    .build();
            kafkaTemplate.send("release-stock", rs);
            return;
        }

        // PENDING -> confirm
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

    @KafkaListener(
            topics = "stock-rejected",
            containerFactory = "stockRejectedKafkaListenerFactory"
    )
    @Transactional
    public void onStockRejected(StockRejected evt) {
        log.info("StockRejected: {}", evt);

        Optional<Order> opt = orderRepository.findById(evt.getOrderId());
        if (opt.isEmpty()) {
            log.warn("Không tìm thấy order {} cho stock-rejected. Bỏ qua.", evt.getOrderId());
            return;
        }

        Order o = opt.get();
        String status = o.getStatus();

        if ("CANCELLED".equals(status)) {
            log.info("Order {} đã CANCELLED trước đó (idempotent). Bỏ qua.", o.getId());
            return;
        }

        if ("CONFIRMED".equals(status)) {
            // Out-of-order: đã confirm mà lại rejected -> không hủy ngược
            log.warn("Order {} đang CONFIRMED nhưng nhận rejected. Bỏ qua.", o.getId());
            return;
        }

        // PENDING -> cancel
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
