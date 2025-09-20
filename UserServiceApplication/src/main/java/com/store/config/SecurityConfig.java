package com.store.config;

import com.store.security.JwtAuthenticationFilter;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/user/allUser").hasAuthority("ROLE_ADMIN")
//                       ========== PRODUCT ===========
                        .requestMatchers(HttpMethod.GET, "/api/user/products/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/user/products").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/user/products/excel/export").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/user/products/excel/import").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/user/products/excel/template").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/user/products/{id}").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/user/products/{id}").hasAuthority("ROLE_ADMIN")
//                       ============ ORDER ===========
                        .requestMatchers(HttpMethod.POST, "/api/user/orders/create").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/orders/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/user/orders/pay/cod").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/user/orders/pay/paypal").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
