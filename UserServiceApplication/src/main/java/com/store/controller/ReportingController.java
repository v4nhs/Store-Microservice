package com.store.controller;

import com.store.service.ReportingService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class ReportingController {

    private final ReportingService reportingService;

    // GET /api/reports/users/{userId}/orders/count?status=CONFIRMED
    @GetMapping("/users/{userId}/orders/count")
    public ResponseEntity<String> countOrdersByUserAndStatus(
            @PathVariable("userId") @NotBlank String userId,
            @RequestParam("status") @NotBlank String status
    ) {
        String msg = reportingService.answerUserOrderCountByStatus(userId, status);
        return ResponseEntity.ok(msg);
    }
}
