package com.store.controller;

import com.store.service.AiQueryService;
import com.store.dto.AiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/products")
@RequiredArgsConstructor
public class ProductAiController {

    private final AiQueryService ai;

    private static boolean notBlank(String s){ return s!=null && !s.isBlank(); }

    public record Ask(String q, String message, String format, Integer topK) {}

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody(required = false) Ask req) {
        String query  = (req!=null && notBlank(req.q())) ? req.q()
                : (req!=null && notBlank(req.message())) ? req.message() : null;
        if (!notBlank(query)) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status","error","message","Thiếu câu hỏi sản phẩm."));
        }
        int topK = (req!=null && req.topK()!=null) ? req.topK() : 5;

        AiResult r = ai.handleProduct(query.trim(), topK);

        return ResponseEntity.status(r.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(r.body());
    }
}
