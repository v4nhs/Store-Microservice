package com.store.controller;

import com.store.service.AiQueryService;
import com.store.dto.AiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/orders")
@RequiredArgsConstructor
public class OrderAiController {

    private final AiQueryService ai;

    private static boolean notBlank(String s){ return s!=null && !s.isBlank(); }

    public record Ask(String q, String message, String format, Integer topK) {}

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody(required = false) Ask req,
                                 @RequestHeader(value="Authorization", required=false) String authHeader,
                                 @RequestHeader(value="X-User-Id", required=false) String userIdHeader) {

        String query  = (req!=null && notBlank(req.q())) ? req.q()
                : (req!=null && notBlank(req.message())) ? req.message() : null;
        if (!notBlank(query)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status","error","message","Thiếu câu hỏi đơn hàng."));
        }
        String format = (req!=null && notBlank(req.format())) ? req.format().toLowerCase() : "json";
        int topK      = (req!=null && req.topK()!=null) ? req.topK() : 5;

        // 1) Chuẩn hoá Bearer từ header gốc
        String bearer = null;
        if (authHeader != null && !authHeader.isBlank()) {
            bearer = authHeader.startsWith("Bearer ") ? authHeader : "Bearer " + authHeader.trim();
        }

        // 2) LẤY userId từ SecurityContext (filter đã set vào Authentication.details)
        String userId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() != null) {
            userId = String.valueOf(auth.getDetails());
        }
        // fallback nếu bạn vẫn muốn cho phép truyền qua header (không bắt buộc)
        if ((userId == null || userId.isBlank()) && userIdHeader != null && !userIdHeader.isBlank()) {
            userId = userIdHeader;
        }

        AiResult r = ai.handleOrder(query.trim(), topK, userId, bearer);
        return ResponseEntity.status(r.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(r.body());
    }
}
