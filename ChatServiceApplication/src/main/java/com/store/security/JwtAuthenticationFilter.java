package com.store.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String[] PUBLIC_PATHS = {
            "/api/ai/products", "/api/ai/products/**",
            "/actuator/**", "/docs/**", "/swagger/**", "/auth/**"
    };

    private boolean isPublic(HttpServletRequest req) {
        String uri = req.getRequestURI();
        org.springframework.util.AntPathMatcher m = new org.springframework.util.AntPathMatcher();
        for (String p : PUBLIC_PATHS) if (m.match(p, uri)) return true;
        return false;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();

        return p.startsWith("/actuator")
                || p.startsWith("/auth")
                || p.startsWith("/docs")
                || p.startsWith("/swagger");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        final String uri = req.getRequestURI();

        if (uri.startsWith("/actuator") || uri.startsWith("/auth") || uri.startsWith("/docs") || uri.startsWith("/swagger")) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7)
                : (auth != null ? auth.trim() : null);

        System.out.println("[CHAT][AUTH] uri=" + uri + " hasAuth=" + (auth != null)
                + " startsBearer=" + (auth != null && auth.startsWith("Bearer ")));

        if (isPublic(req) && (token == null || token.isBlank())) {
            chain.doFilter(req, res);
            return;
        }

        if (token == null || token.isBlank()) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Missing Authorization: Bearer <JWT>\"}");
            return;
        }

        try {
            String userId = JwtUtil.extractUserId(token);
            String username = JwtUtil.extractUsername(token);

            var authToken = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    username, null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
            );
            authToken.setDetails(userId);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authToken);

            System.out.println("[CHAT][AUTH][OK] user=" + username + " userId=" + userId);
            chain.doFilter(req, res);
        } catch (Exception e) {
            System.out.println("[CHAT][AUTH][FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Invalid token\"}");
        }
    }

}
