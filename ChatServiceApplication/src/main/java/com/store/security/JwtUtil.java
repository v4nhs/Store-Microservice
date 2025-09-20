package com.store.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.UUID;

public class JwtUtil {

    // PHẢI đúng 100% với nơi phát token (lấy từ cấu hình/environment)
    private static final String SECRET =
            "Y3ZndQGRXwfnr+Ub6sCBDkri7Z1z8refHJYaaO42OZnyh1d70pHAV7it+bh/81rM";

    private static SecretKey keyRaw() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)); // KHÔNG decode
    }
    private static SecretKey keyB64() {
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));      // CÓ decode
    }

    private static Claims parseWith(SecretKey k, String token) {
        return Jwts.parserBuilder().setSigningKey(k).build()
                .parseClaimsJws(token).getBody();
    }

    /** Parse token: thử RAW trước, fail thì thử Base64. Ném lỗi nếu cả hai đều fail. */
    private static Claims parseClaimsSmart(String token) {
        try {
            Claims c = parseWith(keyRaw(), token);
            // debug nhẹ để bạn biết đang dùng kiểu nào
            System.out.println("[CHAT][JWT] verified with RAW secret");
            return c;
        } catch (JwtException e1) {
            try {
                Claims c = parseWith(keyB64(), token);
                System.out.println("[CHAT][JWT] verified with BASE64 secret");
                return c;
            } catch (JwtException e2) {
                // log ngắn gọn để truy tiếp
                System.out.println("[CHAT][JWT][FAIL] raw=" + e1.getClass().getSimpleName()
                        + " | b64=" + e2.getClass().getSimpleName());
                throw e2; // SignatureException sẽ bị filter catch và trả 401
            }
        }
    }

    public static String extractUserId(String token) {
        Claims c = parseClaimsSmart(token);
        Object raw = c.get("userId"); if (raw == null) raw = c.get("uid");
        if (raw == null) throw new IllegalArgumentException("missing userId claim");
        String uid = Normalizer.normalize(String.valueOf(raw), Normalizer.Form.NFKC)
                .strip().replace("\uFEFF","").replace("\u200B","")
                .replace("\u200E","").replace("\u200F","");
        UUID.fromString(uid);
        return uid;
    }

    public static String extractUsername(String token) {
        return parseClaimsSmart(token).getSubject();
    }
}
