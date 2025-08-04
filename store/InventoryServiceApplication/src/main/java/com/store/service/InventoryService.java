package com.store.service;

import com.store.dto.ProductDto;
import com.store.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final RestTemplate restTemplate;

    public boolean checkStock(OrderEvent event) {

        ProductDto product = restTemplate.getForObject(
                "http://product-service/api/products/" + event.getProductId(),
                ProductDto.class
        );

        HttpEntity<String> entity = null;

        if (product.getQuantity() >= event.getQuantity()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            entity = new HttpEntity<>(null, headers);

            restTemplate.exchange(
                    "http://order-service/api/orders/" + event.getId() + "/status?status=CONFIRMED",
                    HttpMethod.PUT,
                    entity,
                    String.class
            );
        } else {
            restTemplate.exchange(
                    "http://order-service/api/orders/" + event.getId() + "/status?status=REJECTED",
                    HttpMethod.PUT,
                    entity,
                    String.class
            );
        }
        return checkStock(event);
    }
}


