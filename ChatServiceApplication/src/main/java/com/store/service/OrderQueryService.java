package com.store.service;

import com.store.client.OrderClientRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryService {

    private final OrderClientRest client;

    // Bắt các dạng: "đơn 123", "chi tiết đơn 8f3c-...", "order 1002", "mã đơn: ABC123"
    private static final Pattern ORDER_ID_RX = Pattern.compile(
            "(?i)(?:đơn|don|order|mã\\s*đơn|ma\\s*don|id)\\s*[:#\\-]?\\s*([a-z0-9\\-]{3,})"
    );

    /** Trích orderId từ câu hỏi tự nhiên */
    public String extractOrderId(String q) {
        if (q == null) return null;
        Matcher m = ORDER_ID_RX.matcher(q);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    public List<Map<String, Object>> getAllForCurrentUser(String bearer) {
        try {
            return client.getMine(bearer);
        } catch (Exception e) {
            log.error("getAllForCurrentUser error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /** Chi tiết một đơn (chỉ khi thuộc user hiện tại) – cache theo orderId + Bearer */
    @Cacheable(cacheNames = "orderByIdForUser",
            key = "#orderId + ':' + T(java.util.Objects).hash(#bearer)",
            unless = "#result == null || #result.isEmpty()")
    public Map<String, Object> getByIdForCurrentUser(String orderId, String bearer) {
        try {
            return client.getMineById(orderId, bearer);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 4xx từ order-service → không có hoặc không thuộc user
            log.warn("getByIdForCurrentUser 4xx: {}", e.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("getByIdForCurrentUser error: {}", e.getMessage(), e);
            return null;
        }
    }
}
