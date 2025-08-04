package com.store.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

@Component
public class JwtUtil {
    private static final String SECRET_KEY_BASE64 = "Y3ZndQGRXwfnr+Ub6sCBDkri7Z1z8refHJYaaO42OZnyh1d70pHAV7it+bh/81rM";

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_KEY_BASE64));
    public static String extractUserId(String token) {
        // Cắt bỏ "Bearer " nếu có
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Giả sử userId được lưu trong claim "sub" (subject)
        return claims.getSubject();
    }
}