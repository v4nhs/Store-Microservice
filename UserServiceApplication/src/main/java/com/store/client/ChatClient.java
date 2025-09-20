package com.store.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatClient {

    private final RestTemplate restTemplate;

    @Value("${chat.base-url:http://chat-service:8087}")
    private String chatBase;

    /* Helpers */
    private String normalizeBase(String raw) {
        if (raw == null) return "http://chat-service:8087";
        String s = raw.trim();
        s = s.replaceFirst("^http:/([^/])", "http://$1")
                .replaceFirst("^https:/([^/])", "https://$1");
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
    private URI buildUrl(String path) {
        String base = normalizeBase(chatBase);
        return UriComponentsBuilder.fromHttpUrl(base)
                .path(path.startsWith("/") ? path : "/" + path)
                .build(true)
                .toUri();
    }
    private HttpHeaders makeHeaders(String accept, String authHeader) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (accept != null && !accept.isBlank()) {
            h.setAccept(MediaType.parseMediaTypes(accept));
        }

        if (authHeader != null && !authHeader.isBlank()) {
            String v = authHeader.trim();
            if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
                h.setBearerAuth(v.substring(7).trim());
            } else {
                h.set(HttpHeaders.AUTHORIZATION, v);
            }
        }
        return h;
    }
    private String handle(Exception e) {
        if (e instanceof HttpStatusCodeException ex) {
            String bodyErr = ex.getResponseBodyAsString();
            return "[chat-service " + ex.getStatusCode().value() + "] " + (bodyErr == null ? ex.getMessage() : bodyErr);
        }
        return "[chat-service error] " + e.getMessage();
    }

    /* 1) PRODUCT (public) */
    public String askProduct(String q, String format, Integer topK, String authHeader) {
        URI url = buildUrl("/api/ai/products");
        HttpHeaders headers = makeHeaders("application/json, text/plain", authHeader);

        Map<String, Object> body = new HashMap<>();
        body.put("q", q);
        if (format != null && !format.isBlank()) body.put("format", format);
        body.put("topK", topK == null ? 5 : Math.max(1, Math.min(20, topK)));

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return resp.getBody();
        } catch (Exception e) {
            return handle(e);
        }
    }

    /* 2) ORDER (yêu cầu Bearer) */
    public String askOrder(String q, String userId, Integer topK, String authHeader) {
        URI url = buildUrl("/api/ai/orders");
        HttpHeaders headers = makeHeaders("application/json", authHeader);

        Map<String, Object> body = new HashMap<>();
        body.put("q", q);
        if (userId != null && !userId.isBlank()) body.put("userId", userId);
        body.put("topK", topK == null ? 5 : Math.max(1, Math.min(20, topK)));

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return resp.getBody();
        } catch (Exception e) {
            return handle(e);
        }
    }

    /* 3) Backward compatible */
    public String ask(String q, String authHeader) { return askProduct(q, null, 5, authHeader); }
    public String ask(String q, String format, Integer topK, String authHeader) { return askProduct(q, format, topK, authHeader); }
}
