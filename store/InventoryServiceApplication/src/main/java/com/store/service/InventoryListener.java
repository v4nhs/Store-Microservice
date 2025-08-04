package com.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryListener {
    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-created-topic", groupId = "inventory-group")
    public void handleOrder(String message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        OrderEvent event = mapper.readValue(message, OrderEvent.class);
        inventoryService.checkStock(event);
    }
}
