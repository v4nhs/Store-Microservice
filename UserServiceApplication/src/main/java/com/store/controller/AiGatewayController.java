package com.store.controller;

import com.store.service.AiGatewayService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class AiGatewayController {

    private final AiGatewayService service;

    // GET /api/ai/ask?q=...
    @GetMapping(value = "/ask", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> askGet(
            @RequestParam("q") @NotBlank String q,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        return ResponseEntity.ok(service.ask(q, auth));
    }

    // POST /api/ai/ask { "q": "..." }
    @PostMapping(value = "/ask",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> askPost(
            @RequestBody Ask body,
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        return ResponseEntity.ok(service.ask(body.q(), auth));
    }

    public record Ask(@NotBlank String q) {}
}
