package com.store.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@Controller
@RequestMapping("/pay")
public class PaymentRedirectController {

    // Callback/return URL mà bạn đã cấu hình: http://localhost:8086/pay/redirect
    @GetMapping("/redirect")
    public ResponseEntity<Void> handleRedirect(
            @RequestParam("orderId") String orderId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(required = false) String resultCode,
            @RequestParam(required = false) String extraData
    ) {
        // TODO: Nếu cần, verify chữ ký, cập nhật trạng thái payment theo provider ở đây.

        // Redirect về trang FE của bạn (ví dụ SPA route /payment/success)
        String target = "/payment/success?orderId=" + orderId + "&status=" + (status == null ? "OK" : status);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
