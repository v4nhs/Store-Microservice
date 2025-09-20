package com.store.service;

import com.store.client.ChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiGatewayService {

    private final ChatClient chatClient;

    // Heuristic: nếu câu hỏi dạng "ảnh/hình..." thì trả link (text/plain) từ chat-service/product
    private static final Pattern IMG_Q =
            Pattern.compile("(?i)^(?:hãy|vui lòng|xin)?\\s*(?:xem|cho|hiển\\s*thị|hien\\s*thi)?\\s*(ảnh|hình|image|img|photo)(?:\\s+(?:của|cua))?\\s+.+");

    /* ==========================
       PRODUCT (public)
       ========================== */

    /** Đơn giản: hỏi product, tự suy đoán format (link nếu là ảnh, ngược lại json) */
    public String askProduct(String q, String bearer) {
        return askProduct(q, null, null, bearer);
    }

    /** Đầy đủ tham số: có thể chỉ định format ("link"/"json") và topK (RAG) */
    public String askProduct(String q, String format, Integer topK, String bearer) {
        if (!StringUtils.hasText(q)) return "{\"status\":\"error\",\"message\":\"Bạn vui lòng nhập câu hỏi.\"}";
        String f = (format == null || format.isBlank())
                ? (isImageQuestion(q) ? "link" : "json")
                : format.trim().toLowerCase();
        // ChatClient nên post tới chat-service /api/ai/product
        return chatClient.askProduct(q.trim(), f, topK, bearer);
    }

    /* ==========================
       ORDER (yêu cầu đăng nhập)
       ========================== */

    /** Hỏi order: luôn trả JSON, cần Bearer; (userId nếu bạn muốn forward rõ ràng) */
    public String askOrder(String q, String userId, String bearer) {
        return askOrder(q, userId, null, bearer);
    }

    public String askOrder(String q, String userId, Integer topK, String bearer) {
        if (!StringUtils.hasText(q)) return "{\"status\":\"error\",\"message\":\"Bạn vui lòng nhập câu hỏi.\"}";
        if (!StringUtils.hasText(bearer)) return "{\"status\":\"error\",\"message\":\"Thiếu Authorization Bearer token.\"}";
        // Luôn JSON cho order
        return chatClient.askOrder(q.trim(), userId, topK, bearer);
    }

    /* ==========================
       Tương thích ngược (/api/ai/ask cũ)
       Mặc định route về product
       ========================== */
    public String ask(String q, String bearer) {
        return askProduct(q, bearer);
    }

    public String ask(String q, String format, Integer topK, String bearer) {
        return askProduct(q, format, topK, bearer);
    }

    /* ========================== */

    private static boolean isImageQuestion(String q) {
        return q != null && IMG_Q.matcher(q.trim()).matches();
    }
}
