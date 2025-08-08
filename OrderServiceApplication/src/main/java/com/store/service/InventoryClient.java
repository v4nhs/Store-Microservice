package com.store.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class InventoryClient {

    private final RestTemplate restTemplate;

    @Autowired
    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Gọi API inventory-service để giảm tồn kho (reserve stock)
    public boolean reserveStock(String productId, int quantity) {
        String url = "http://inventory-service/api/inventory/" + productId + "/decrease?quantity=" + quantity;

        try {
            restTemplate.postForEntity(url, null, Void.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
