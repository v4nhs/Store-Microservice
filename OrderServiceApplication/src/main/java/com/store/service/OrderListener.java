package com.store.service;

import com.store.dto.OrderDTO;
import com.store.event.ReleaseStock;
import com.store.event.StockRejected;
import com.store.event.StockReserved;
import com.store.model.Order;
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

import java.util.Map;
import java.util.Optional;

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
    @KafkaListener(topics = "stock-reserved", containerFactory = "stockReservedKafkaListenerFactory")
    @Transactional
    public void onStockReserved(StockReserved evt) {
        var o = orderRepository.findById(evt.getOrderId()).orElse(null);
        if (o == null) {
            kafkaTemplate.send("release-stock",
                    ReleaseStock.builder().orderId(evt.getOrderId()).productId(evt.getProductId()).quantity(evt.getQuantity()).build());
            return;
        }

        orderItemRepository.findByOrderIdAndProductId(o.getId(), evt.getProductId())
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

        orderItemRepository.findByOrderIdAndProductId(o.getId(), evt.getProductId())
                .ifPresent(i -> i.setItemStatus(OrderItemStatus.REJECTED));

        if (o.getStatus() != OrderStatus.CANCELLED) {
            o.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(o);

            // release các item đã RESERVED
            o.getItems().stream()
                    .filter(i -> i.getItemStatus() == OrderItemStatus.RESERVED)
                    .forEach(i -> kafkaTemplate.send("release-stock",
                            ReleaseStock.builder().orderId(o.getId()).productId(i.getProductId()).quantity(i.getQuantity()).build()));

            kafkaTemplate.send("order-cancelled",
                    OrderDTO.builder().orderId(o.getId()).userId(o.getUserId()).status(o.getStatus().name()).build());
        }
    }
//    @KafkaListener(
//            topics = "payment-failed"
//            // , containerFactory = "paymentFailedKafkaListenerFactory"
//    )
//    @Transactional
//    public void onPaymentFailed(Map<String, Object> evt) {
//        String orderId = (String) evt.get("orderId");
//        String paymentId = (String) evt.get("paymentId");
//        String reason = (String) evt.get("reason");
//
//        log.info("PaymentFailed: orderId={}, paymentId={}, reason={}", orderId, paymentId, reason);
//
//        Optional<Order> opt = orderRepository.findById(orderId);
//        if (opt.isEmpty()) {
//            log.warn("Không tìm thấy order {} cho payment-failed. Bỏ qua.", orderId);
//            return;
//        }
//
//        Order o = opt.get();
//        OrderStatus status = o.getStatus();
//
//        if (status == OrderStatus.CANCELLED) {
//            log.info("Order {} đã CANCELLED trước đó (idempotent). Bỏ qua.", o.getId());
//            return;
//        }
//        if (status == OrderStatus.PAID) {
//            log.warn("Order {} đã PAID nhưng nhận payment-failed (out-of-order). Bỏ qua.", o.getId());
//            return;
//        }
//
//        // Hủy đơn vì thanh toán thất bại
//        o.setStatus(OrderStatus.CANCELLED);
//        orderRepository.save(o);
//
//        OrderDTO cancelled = OrderDTO.builder()
//                .orderId(o.getId())
//                .userId(o.getUserId())
//                .productId(o.getProductId())
//                .quantity(o.getQuantity())
//                .status(o.getStatus().name()) // "CANCELLED"
//                .build();
//
//        kafkaTemplate.send("order-cancelled", cancelled);
//        log.info("Published 'order-cancelled' for {} (reason={})", o.getId(), reason);
//    }

}
