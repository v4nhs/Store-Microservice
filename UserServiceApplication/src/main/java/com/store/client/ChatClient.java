package com.store.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatClient {
    private final RestTemplate restTemplate;

    @Value("${chat.base-url:http://chat-service}")
    private String chatBase;

    public String ask(String q, String authHeader) {
        String url = chatBase + "/api/ai/ask";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authHeader != null && !authHeader.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }

        Map<String, Object> body = Map.of(
                "message", q,
                "topK", 5
        );

        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );
        return resp.getBody();
    }
}
