package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.OrderStatusUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderKafkaConsumer {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, OrderStatusUpdateDTO> kafkaTemplate;

    @KafkaListener(topics = "order_topic", groupId = "inventory")
    public void consumeOrder(OrderDTO orderDTO) {
        boolean isAvailable = inventoryService.checkStock(orderDTO.getItems());

        OrderStatusUpdateDTO statusUpdate = new OrderStatusUpdateDTO();
        statusUpdate.setOrderId(orderDTO.getOrderId());
        statusUpdate.setStatus(isAvailable ? "CONFIRMED" : "FAILED");
        log.info("Received order: {}", orderDTO);
        kafkaTemplate.send("order_status_topic", statusUpdate);
    }
}
