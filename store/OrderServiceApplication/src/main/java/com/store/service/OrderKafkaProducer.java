package com.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.model.Order;
import com.store.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderKafkaProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendOrderCreatedEvent(Order order) {
        OrderEvent event = new OrderEvent(order.getId(), order.getProductId(), order.getStatus(), order.getQuantity());
        try {
            String message = new ObjectMapper().writeValueAsString(event);
            kafkaTemplate.send("order-created-topic", message);

            System.out.println("Đã gửi Kafka event: " + event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
