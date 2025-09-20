package com.store.controller;

import com.store.service.AiGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class AiGatewayController {

    private final AiGatewayService service;

    private static final Pattern IMG_Q =
            Pattern.compile("(?i)^(?:hãy|vui lòng|xin)?\\s*(?:xem|cho|hiển\\s*thị|hien\\s*thi)?\\s*(ảnh|hình|image|img|photo)(?:\\s+(?:của|cua))?\\s+.+");

    /* ==========================
       1) Hỏi về PRODUCT (public)
       ========================== */
    @PostMapping(
            value = "/products",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE },
            produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE }
    )
    public ResponseEntity<String> askProduct(
            @RequestBody(required = false) Ask body,
            @RequestParam(value = "q", required = false) String qParam,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        String q = (body != null && StringUtils.hasText(body.q)) ? body.q : qParam;
        if (!StringUtils.hasText(q)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Thiếu tham số q.\"}");
        }

        String result = service.askProduct(q, auth);

        MediaType mt = isImageQuestion(q) ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(mt).body(result);
    }

    /* ========================
       2) Hỏi về ORDER (secure)
       ======================== */
    @PostMapping(
            value = "/orders",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> askOrder(
            @RequestBody(required = false) Ask body,
            @RequestParam(value = "q", required = false) String qParam,
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestHeader(value = "X-User-Id", required = false) String userId // nếu bạn muốn forward rõ ràng
    ) {
        String q = (body != null && StringUtils.hasText(body.q)) ? body.q : qParam;
        if (!StringUtils.hasText(q)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Thiếu tham số q.\"}");
        }

        // YÊU CẦU ĐĂNG NHẬP
        if (!StringUtils.hasText(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Thiếu Authorization Bearer token.\"}");
        }

        // Gọi xuống chat-service (order) – forward Bearer (+ userId nếu có)
        String result = service.askOrder(q, userId, auth);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    /* =========================
       (Tuỳ chọn) endpoint cũ /ask
       Giữ cho tương thích ngược.
       ========================= */
    @PostMapping(
            value = "/ask",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE },
            produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE }
    )
    public ResponseEntity<String> askUnified(
            @RequestBody(required = false) Ask body,
            @RequestParam(value = "q", required = false) String qParam,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        String q = (body != null && StringUtils.hasText(body.q)) ? body.q : qParam;
        if (!StringUtils.hasText(q)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Thiếu tham số q.\"}");
        }
        String result = service.askProduct(q, auth);
        MediaType mt = isImageQuestion(q) ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok().contentType(mt).body(result);
    }

    private static boolean isImageQuestion(String q) {
        return q != null && IMG_Q.matcher(q.trim()).matches();
    }

    public static class Ask {
        public String q;
    }
}
