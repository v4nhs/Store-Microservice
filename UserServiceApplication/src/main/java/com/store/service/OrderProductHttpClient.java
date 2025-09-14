package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class OrderProductHttpClient {

    private final RestTemplate restTemplate;

    // Gọi OrderService
    public OrderDTO getOrderById(String orderId) {
        try {
            return restTemplate.getForObject("http://order-service/api/orders/{id}", OrderDTO.class, orderId);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) return null;
            throw ex;
        }
    }

    public Long countOrdersByUserAndStatus(String userId, String status) {
        try {
            ResponseEntity<Long> res = restTemplate.getForEntity(
                    "http://order-service/api/orders/count?userId={userId}&status={status}",
                    Long.class, userId, status);
            return res.getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) return 0L;
            throw ex;
        }
    }

    // Gọi ProductService
    public ProductDTO getProductById(String productId) {
        try {
            return restTemplate.getForObject("http://product-service/api/products/{id}", ProductDTO.class, productId);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) return null;
            throw ex;
        }
    }
}