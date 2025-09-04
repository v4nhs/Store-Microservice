package com.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ToolGateway {

    private final WebClient.Builder webClient;

    @Value("${app.user-service.base-url:lb://user-service}")
    private String userServiceBase;

    @Value("${app.payment-service.base-url:lb://payment-service}")
    private String paymentServiceBase;

    public Mono<String> getOrderStatus(String orderId) {
        return webClient.build()
                .get()
                .uri(userServiceBase + "/api/user/orders/status/{id}", orderId)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> payWithCod(String orderId) {
        String body = String.format("{\"orderId\":\"%s\",\"idempotencyKey\":\"chat-cod\"}", orderId);
        return webClient.build()
                .post()
                .uri(userServiceBase + "/api/user/orders/pay/cod")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> startPaypal(String orderId) {
        String body = String.format("{\"orderId\":\"%s\",\"idempotencyKey\":\"chat-paypal\"}", orderId);
        return webClient.build()
                .post()
                .uri(userServiceBase + "/api/user/orders/pay/paypal")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class);
    }
}
