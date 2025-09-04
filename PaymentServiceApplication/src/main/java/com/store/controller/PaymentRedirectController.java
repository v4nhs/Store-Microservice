package com.store.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/pay")
public class PaymentRedirectController {

    private final RestTemplate lbRestTemplate;

    public PaymentRedirectController(@Qualifier("lbRestTemplate") RestTemplate lbRestTemplate) {
        this.lbRestTemplate = lbRestTemplate;
    }

    @GetMapping(value = "/redirect", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleRedirect(
            @RequestParam(value = "orderId", required = false) String orderId,       // MOMO/COD
            @RequestParam(value = "token",   required = false) String paypalOrderId, // PayPal v2
            @RequestParam(value = "PayerID", required = false) String payerId,       // PayPal (không bắt buộc)
            @RequestParam(value = "status",  required = false) String status         // MOMO/COD
    ) {
        String html;

        // --- PAYPAL: có token -> gọi capture rồi trả HTML ---
        if (paypalOrderId != null && !paypalOrderId.isBlank()) {
            String enc = URLEncoder.encode(paypalOrderId, StandardCharsets.UTF_8);
            String captureUrl = "http://payment-service/api/payments/paypal/capture?paypalOrderId=" + enc;

            String captureResult = "OK";
            try {
                lbRestTemplate.postForEntity(captureUrl, HttpEntity.EMPTY, String.class);
            } catch (Exception ex) {
                captureResult = "FAILED: " + escapeHtml(ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }

            html = page("""
                <h1>PayPal Payment</h1>
                <p><b>paypalOrderId:</b> %s</p>
                <p><b>PayerID:</b> %s</p>
                <p><b>Capture:</b> %s</p>
                <hr/>
                <p>Payment has been processed on the server. You can now close this tab.</p>
                """.formatted(escapeHtml(paypalOrderId),
                    escapeHtml(payerId),
                    captureResult));
            return ResponseEntity.ok(html);
        }

        // --- MOMO/COD: có orderId -> trả HTML ---
        if (orderId != null && !orderId.isBlank()) {
            String st = (status == null || status.isBlank()) ? "OK" : status;
            html = page("""
                <h1>Payment</h1>
                <p><b>orderId:</b> %s</p>
                <p><b>status:</b> %s</p>
                <hr/>
                <p>Payment status received. You can now close this tab.</p>
                """.formatted(escapeHtml(orderId), escapeHtml(st)));
            return ResponseEntity.ok(html);
        }

        // --- Thiếu cả token lẫn orderId ---
        html = page("""
            <h1>Bad Request</h1>
            <p>Missing <code>token</code> (PayPal).</p>
            """);
        return ResponseEntity.badRequest().body(html);
    }


    @GetMapping(value = "/paypal/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paypalReturn(@RequestParam("token") String token,
                                               @RequestParam(value = "PayerID", required = false) String payerId) {
        return handleRedirect(null, token, payerId, null);
    }

    @GetMapping(value = "/paypal/cancel", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paypalCancel(@RequestParam("token") String token) {
        String html = page("""
            <h1>PayPal Payment</h1>
            <p><b>paypalOrderId:</b> %s</p>
            <p><b>Status:</b> CANCELED</p>
            """.formatted(escapeHtml(token)));
        return ResponseEntity.ok(html);
    }

    // ===== Helpers =====
    private static String page(String body) {
        return """
            <!doctype html>
            <html lang="en"><head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>Payment Result</title>
              <style>
                body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
                     background:#f6f7f9;margin:0;padding:24px;}
                .card{max-width:720px;margin:0 auto;background:#fff;border-radius:16px;
                      box-shadow:0 2px 14px rgba(0,0,0,.06);padding:24px;}
                h1{margin:0 0 12px;font-size:22px}
                p{margin:6px 0 0}
                hr{margin:16px 0;border:0;border-top:1px solid #eee}
                code{background:#f1f1f1;padding:2px 6px;border-radius:6px}
              </style>
            </head><body><div class="card">%s</div></body></html>
            """.formatted(body == null ? "" : body);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
