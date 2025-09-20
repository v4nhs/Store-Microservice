package com.store.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.UUID;

@Component
public class JwtUtil {

    // PHẢI giống nơi phát token (auth-service) và user-service
    private static final String JWT_SECRET =
            "Y3ZndQGRXwfnr+Ub6sCBDkri7Z1z8refHJYaaO42OZnyh1d70pHAV7it+bh/81rM";

    private final SecretKey key;

    public JwtUtil() {
        this.key = deriveKey(JWT_SECRET);
    }

    /**
     * Lấy userId từ claim "userId" (fallback "uid"), làm sạch, và validate UUID.
     * Không dùng subject/username ở đây.
     */
    public String extractUserId(String token) {
        Claims claims = getClaims(token);

        Object raw = claims.get("userId");
        if (raw == null) raw = claims.get("uid");
        if (raw == null) {
            throw new IllegalArgumentException("missing userId claim");
        }

        String uid = clean(String.valueOf(raw));
        UUID.fromString(uid); // ném IllegalArgumentException nếu không hợp lệ
        return uid;
    }

    /** (Tuỳ chọn) Nếu nơi khác cần username */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /** Hỗ trợ cả secret Base64 và secret raw UTF-8 */
    private static SecretKey deriveKey(String secret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            return Keys.hmacShaKeyFor(decoded);
        } catch (IllegalArgumentException notBase64) {
            // Không phải Base64 → dùng UTF-8 bytes trực tiếp
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Làm sạch ký tự vô hình/whitespace Unicode để tránh lỗi UUID.fromString */
    private static String clean(String s) {
        if (s == null) return null;
        return Normalizer.normalize(s, Normalizer.Form.NFKC)
                .strip()
                .replace("\uFEFF", "")  // BOM
                .replace("\u200B", "")  // ZERO WIDTH SPACE
                .replace("\u200E", "")  // LRM
                .replace("\u200F", ""); // RLM
    }
}
