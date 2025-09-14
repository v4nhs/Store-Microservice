package com.store.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiProxyController {

    private final RestTemplate restTemplate;

    @Value("${chat.base-url:http://chat-service}")
    private String chatBase;

    // POST /api/ai/chat  -> proxy sang chat-service /api/ai/chat
    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody Map<String, Object> body,
                                       @RequestHeader(value = "Authorization", required = false) String auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth != null && !auth.isBlank()) headers.set("Authorization", auth);

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        String url = chatBase + "/api/ai/chat";
        return restTemplate.exchange(url, HttpMethod.POST, req, String.class);
    }

    // POST /api/ai/ingest/text -> proxy sang chat-service /api/ai/ingest/text
    @PostMapping("/ingest/text")
    public ResponseEntity<String> ingestText(@RequestBody Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

        String url = chatBase + "/api/ai/ingest/text";
        return restTemplate.exchange(url, HttpMethod.POST, req, String.class);
    }

    // POST /api/ai/ingest/file (multipart) -> proxy sang chat-service /api/ai/ingest/file
    @PostMapping(value = "/ingest/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> ingestFile(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"File trống\"}");
        }
        // Chuẩn bị multipart body
        ByteArrayResource fileRes = new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileRes, partHeaders);
        form.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(form, headers);
        String url = chatBase + "/api/ai/ingest/file";
        return restTemplate.exchange(url, HttpMethod.POST, req, String.class);
    }
}
