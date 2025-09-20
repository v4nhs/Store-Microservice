package com.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO kết quả chuẩn hoá cho các AI endpoint.
 * Controller dùng:
 *   ResponseEntity.status(r.status()).body(r.body());
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResult {

    private HttpStatus httpStatus;
    private String status;        // ok | not_found | error
    private String type;          // image | price | stock | sizes | text ...
    private Map<String, Object> data;
    private String plain;         // plain text/url
    private String redirectUrl;   // URL redirect (nếu cần)

    public static AiResult text(String ans){
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("answer", ans);
        return AiResult.builder()
                .httpStatus(HttpStatus.OK)
                .status("ok")
                .type("text")
                .data(d)
                .build();
    }

    public static AiResult notFound(String msg){
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("message", msg);
        return AiResult.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .status("not_found")
                .type("text")
                .data(d)
                .build();
    }

    public static AiResult error(HttpStatus code, String msg){
        Map<String,Object> d = new LinkedHashMap<>();
        d.put("message", msg);
        return AiResult.builder()
                .httpStatus(code)
                .status("error")
                .type("text")
                .data(d)
                .build();
    }

    public static AiResult ok(String type, Map<String,Object> data){
        return AiResult.builder()
                .httpStatus(HttpStatus.OK)
                .status("ok")
                .type(type)
                .data(data)
                .build();
    }

    /* ========= Phương thức controller đang gọi ========= */
    /** Dùng cho ResponseEntity.status(...) */
    public HttpStatus status() {
        return httpStatus != null ? httpStatus : HttpStatus.OK;
    }

    /** Dùng cho ResponseEntity.body(...) */
    public Object body() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("type", type);
        if (data != null) out.put("data", data);
        if (plain != null) out.put("plain", plain);
        if (redirectUrl != null) out.put("redirectUrl", redirectUrl);
        return out;
    }
}
