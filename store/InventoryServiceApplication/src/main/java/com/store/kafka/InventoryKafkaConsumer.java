package com.store.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.event.OrderEvent;
import com.store.event.OrderStatusUpdate;
import com.store.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryKafkaConsumer {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryKafkaConsumer(InventoryService inventoryService, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-created-topic", groupId = "inventory-group")
    public void consume(String orderEventStr) throws JsonProcessingException {
        OrderEvent orderEvent = new ObjectMapper().readValue(orderEventStr, OrderEvent.class);

        boolean inStock = inventoryService.checkStock(orderEvent);

        String status = inStock ? "CONFIRMED" : "REJECTED";

        OrderStatusUpdate update = new OrderStatusUpdate(orderEvent.getId(), status);
        kafkaTemplate.send("order-status-updated-topic", new ObjectMapper().writeValueAsString(update));
    }
}
