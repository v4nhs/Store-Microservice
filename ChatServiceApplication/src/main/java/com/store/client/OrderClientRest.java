package com.store.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderClientRest {
    private final RestTemplate restTemplate;

    @Value("${order.service.base-url:http://localhost:8083}")
    private String baseUrl;

    /** Lấy tất cả đơn hàng */
    public List<Map<String, Object>> getMine(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List> res = restTemplate.exchange(
                baseUrl + "/api/orders/mine",
                HttpMethod.GET,
                entity,
                List.class
        );
        return res.getBody();
    }

    public Map<String, Object> getMineById(String orderId, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) headers.set(HttpHeaders.AUTHORIZATION, bearer);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> res = restTemplate.exchange(
                baseUrl + "/api/orders/mine/{id}",
                HttpMethod.GET,
                entity,
                Map.class,
                orderId
        );
        return res.getBody();
    }
}
