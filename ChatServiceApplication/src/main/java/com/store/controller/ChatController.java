package com.store.controller;

import com.store.service.RagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.ai.document.Document;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final RagService rag;

    /* =================== ASK =================== */

    // POST JSON -> trả về text/plain (hợp với ChatClient RestTemplate)
    @PostMapping(
            value = "/ask",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> askJson(@RequestBody(required = false) ChatReq req) {
        String query = (req == null) ? null
                : (notBlank(req.message) ? req.message
                : (notBlank(req.q) ? req.q : null));
        if (!notBlank(query)) {
            return ResponseEntity.badRequest()
                    .body("Missing query. Use field 'message' or 'q'.");
        }
        int k = (req == null || req.topK == null) ? 5 : Math.max(1, Math.min(20, req.topK));
        String answer = rag.ask(query.trim(), k);
        return ResponseEntity.ok(answer);
    }

    // (Tuỳ chọn) GET cho các client cũ: /api/ai/ask?q=...&topK=5
    @GetMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> askGet(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(value = "topK", required = false) Integer topK
    ) {
        if (!notBlank(q)) {
            return ResponseEntity.badRequest().body("Missing query param 'q'.");
        }
        int k = topK == null ? 5 : Math.max(1, Math.min(20, topK));
        return ResponseEntity.ok(rag.ask(q.trim(), k));
    }

    /* =================== INGEST =================== */

    @PostMapping(value = "/ingest/text", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingest(@Valid @RequestBody IngestReq req) {
        rag.ingestText(req.text, req.meta);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingestFile(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "File trống"));
        }
        var reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        var splitter = new TokenTextSplitter();
        var docs = splitter.apply(reader.read());

        for (Document d : docs) {
            rag.ingestText(d.getText(), d.getMetadata());
        }
        return ResponseEntity.ok(Map.of("status", "ok", "chunks", docs.size()));
    }

    /* =================== DTO nội bộ (nới lỏng ràng buộc) =================== */
    public static class ChatReq {
        public String message;         // không @NotBlank để chấp nhận null
        public String q;               // hỗ trợ cả 'q'
        @Min(1) @Max(20) public Integer topK;
    }
    public static class IngestReq {
        @NotBlank public String text;
        public Map<String, Object> meta;
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
