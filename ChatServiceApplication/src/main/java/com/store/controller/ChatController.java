package com.store.controller;


import com.store.dto.request.IngestTextRequest;
import com.store.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final RagService rag;

    /* =================== INGEST =================== */

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping(value = "/ingest/text", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingest(@Valid @RequestBody IngestTextRequest req) {
        rag.ingestText(req.text, req.meta);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // Chỉ ADMIN mới được ingest file
    @PreAuthorize("hasRole('ROLE_ADMIN')")
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
}
