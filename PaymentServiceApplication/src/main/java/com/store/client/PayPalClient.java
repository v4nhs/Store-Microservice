package com.store.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.PaypalErr;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Log4j2
public class PayPalClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper om;

    public PayPalClient(@Qualifier("externalRestTemplate") RestTemplate restTemplate, ObjectMapper om) {
        this.restTemplate = restTemplate;
        this.om = om;
    }

    @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}")
    private String baseUrl;
    @Value("${paypal.client-id}")
    private String clientId;
    @Value("${paypal.client-secret}")
    private String clientSecret;

    // ===== Helpers =====

    /**
     * Chuẩn hoá giá trị gửi cho PayPal: dùng số lẻ theo currency (USD=2, JPY=0, …)
     */
    private static String formatAmount(BigDecimal amount, String currency) {
        int scale = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
        if (scale < 0) scale = 2;
        return amount.setScale(scale, RoundingMode.DOWN).toPlainString();
    }

    public static String printUpToNNoTrailingZeros(BigDecimal v, int n) {
        if (v == null) return null;
        return v.setScale(n, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** In số cho UI/log: bỏ số 0 thừa, không ép về n chữ số */
    public static String printNoTrailingZeros(BigDecimal v) {
        if (v == null) return null;
        return v.stripTrailingZeros().toPlainString();
    }

    // ===== PayPal calls =====

    public String accessToken() {
        String url = baseUrl + "/v1/oauth2/token";
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(clientId, clientSecret);
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");

        var resp = restTemplate.postForEntity(url, new HttpEntity<>(form, h), Map.class);
        return (String) Objects.requireNonNull(resp.getBody()).get("access_token");
    }

    public CreateOrderResult createOrder(BigDecimal amount, String currency,
                                         String returnUrl, String cancelUrl, String referenceId) {
        String token = accessToken();
        String url = baseUrl + "/v2/checkout/orders";

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("intent", "CAPTURE");

        Map<String, Object> amountObj = Map.of(
                "currency_code", currency,
                "value", formatAmount(amount, currency) // ✅ cắt bớt theo currency; không làm tròn lên
        );

        Map<String, Object> pu = new HashMap<>();
        pu.put("amount", amountObj);
        if (referenceId != null) pu.put("reference_id", referenceId);

        payload.put("purchase_units", List.of(pu));
        payload.put("application_context", Map.of(
                "return_url", returnUrl,
                "cancel_url", cancelUrl
        ));

        ResponseEntity<Map> resp = restTemplate.postForEntity(url, new HttpEntity<>(payload, h), Map.class);
        Map body = Objects.requireNonNull(resp.getBody());
        String id = (String) body.get("id");
        String approval = null;

        List<Map<String, String>> links = (List<Map<String, String>>) body.get("links");
        if (links != null) {
            for (Map<String, String> l : links) {
                if ("approve".equals(l.get("rel"))) { approval = l.get("href"); break; }
            }
        }
        return new CreateOrderResult(id, approval);
    }

    public Map<String, Object> capture(String orderId) {
        try {
            ResponseEntity<Map<String,Object>> res = restTemplate.exchange(
                    "https://api-m.sandbox.paypal.com/v2/checkout/orders/{id}/capture",
                    HttpMethod.POST,
                    new HttpEntity<>(createHeadersWithBearer()),
                    new ParameterizedTypeReference<Map<String, Object>>() {},
                    orderId
            );
            return res.getBody();
        } catch (HttpClientErrorException e) {
            String raw = e.getResponseBodyAsString();
            String summary = "PAYPAL " + e.getStatusCode().value() + " " + e.getStatusText();

            try {
                PaypalErr err = om.readValue(raw, PaypalErr.class);
                String issue = err.firstIssue();
                log.warn("[PAYPAL][ERR] {} / {} - {} (debug_id={})",
                        err.name, issue, err.message, err.debug_id);
                if (err.links != null) {
                    err.links.stream()
                            .filter(l -> "information_link".equalsIgnoreCase(l.rel))
                            .findFirst()
                            .ifPresent(l -> log.warn("[PAYPAL][DOC] {}", l.href));
                }
                throw new ResponseStatusException(
                        e.getStatusCode(),
                        err.name + " / " + issue + " - " + err.message + " (debug_id=" + err.debug_id + ")",
                        e
                );
            } catch (Exception parseEx) {
                log.warn("[PAYPAL][ERR] {} rawBody={}", summary, raw);
                throw new ResponseStatusException(e.getStatusCode(), summary, e);
            }
        }
    }

    private HttpHeaders createHeadersWithBearer() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }
    public record CreateOrderResult(String orderId, String approvalUrl) {}
}
