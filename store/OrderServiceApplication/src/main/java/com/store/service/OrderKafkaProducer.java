package com.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.OrderDTO;
import com.store.model.Order;
import com.store.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendOrderCreatedEvent(Order order) {
        try {
            String json = objectMapper.writeValueAsString(order);
            kafkaTemplate.send("order_topic", json);
        } catch (JsonProcessingException e) {
            e.printStackTrace(); // hoặc log lỗi
        }
    }
}
