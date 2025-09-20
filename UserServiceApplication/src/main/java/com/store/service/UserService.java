package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import com.store.dto.UserDTO;
import com.store.model.User;
import com.store.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;

    @Cacheable(value = "allUsers", key = "'all'")
    public List<User> getAllUser(){
        System.out.println("Get all User first on.....");
        return userRepository.findAll();
    }
    public UserDTO getUserByUsername(String username) {
        String url = "http://user-service/api/users/" + username;
        return restTemplate.getForObject(url, UserDTO.class);
    }
    private HttpHeaders createAuthHeaders(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            if (!token.startsWith("Bearer ")) token = "Bearer " + token;
            headers.set("Authorization", token);
        }
        return headers;
    }

    // ========= PRODUCT APIs giữ nguyên (proxy sang product-service) =========

    public ProductDTO createProduct(ProductDTO productDto, HttpServletRequest request) {
        try {
            logger.info("Authorization token from request: {}", request.getHeader("Authorization"));
            HttpHeaders headers = createAuthHeaders(request);
            HttpEntity<ProductDTO> httpEntity = new HttpEntity<>(productDto, headers);
            ResponseEntity<ProductDTO> response = restTemplate.postForEntity(
                    "http://product-service/api/products", httpEntity, ProductDTO.class);
            logger.info("Headers to send: {}", headers);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Lỗi tạo sản phẩm: {}", e.getMessage(), e);
            return null;
        }
    }
    public List<ProductDTO> getAllProducts() {
        ResponseEntity<List<ProductDTO>> response = restTemplate.exchange(
                "http://product-service/api/products",
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }
    public ProductDTO getProductById(String id) {
        return restTemplate.getForObject("http://product-service/api/products/" + id, ProductDTO.class);
    }
    public void deleteProduct(String id) {
        restTemplate.delete("http://product-service/api/products/" + id);
    }
    public ProductDTO updateProduct(String id, ProductDTO dto) {
        restTemplate.put("http://product-service/api/products/" + id, dto);
        return getProductById(id);
    }

    // ========= ORDER =========

    /** Multi-items order: mỗi item có productId + size + quantity; user-service chỉ proxy */
    public String placeOrder(List<OrderDTO> items, HttpServletRequest request) {
        try {
            if (items == null || items.isEmpty()) return "Danh sách sản phẩm trống";

            // lấy userId từ JWT
            String rawAuth = request.getHeader("Authorization");
            String bare = rawAuth;

            if (bare != null && bare.startsWith("Bearer ")) bare = bare.substring(7);
            String userId = null;
            try {
                if (bare != null && !bare.isBlank()) {
                    Claims claims = Jwts.parser()
                            .setSigningKey("Y3ZndQGRXwfnr+Ub6sCBDkri7Z1z8refHJYaaO42OZnyh1d70pHAV7it+bh/81rM")
                            .parseClaimsJws(bare)
                            .getBody();
                    userId = claims.get("userId", String.class);
                }
            } catch (Exception ignore) {}
            logger.info("[USER→ORDER] will create order for userId={}", userId);
            // payload đúng contract của order-service
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);
            List<Map<String, Object>> reqItems = new ArrayList<>();
            for (OrderDTO dto : items) {
                if (dto.getProductId() == null || dto.getProductId().isBlank()
                        || dto.getQuantity() == null || dto.getQuantity() < 1) {
                    return "Sản phẩm không hợp lệ (productId/quantity)";
                }
                if (dto.getSize() == null || dto.getSize().isBlank()) {
                    return "Thiếu size cho productId=" + dto.getProductId();
                }
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("productId", dto.getProductId());
                it.put("size", dto.getSize());          // <-- bắt buộc có size
                it.put("quantity", dto.getQuantity());
                reqItems.add(it);
            }
            payload.put("items", reqItems);

            HttpHeaders headers = createAuthHeaders(request); // forward Authorization
            ResponseEntity<OrderDTO> response = restTemplate.exchange(
                    "http://order-service/api/orders/create",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    OrderDTO.class
            );

            OrderDTO order = response.getBody();
            if (order == null) return "Order creation failed";
            BigDecimal total = order.getTotalAmount();
            return "Created order id=" + order.getId()
                    + ", status=" + order.getStatus()
                    + ", total=" + (total != null ? total : BigDecimal.ZERO);
        } catch (HttpClientErrorException e) {
            logger.error("Đặt hàng (multi) thất bại ({}): {}", e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            return "Order creation failed: " + e.getStatusCode().value() + " " + e.getStatusText();
        } catch (Exception e) {
            logger.error("Đặt hàng (multi) thất bại: {}", e.getMessage(), e);
            return "Order creation failed due to error";
        }
    }

    public List<OrderDTO> getAllOrder() {
        ResponseEntity<List<OrderDTO>> response = restTemplate.exchange(
                "http://order-service/api/orders",
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        return response.getBody();
    }
    public OrderDTO getOrderById(String id) {
        return restTemplate.getForObject("http://order-service/api/orders/" + id, OrderDTO.class);
    }
    public String payOrderWithMethod(String orderId, String idempotencyKey, String method, String provider,
                                     HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isBlank()) headers.setBearerAuth(token);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                headers.set("Idempotency-Key", idempotencyKey);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("amount", null);
            body.put("idempotencyKey", idempotencyKey);
            body.put("method", method);
            if ("PAYPAL".equalsIgnoreCase(method)) {
                body.put("provider", "PAYPAL");
                body.put("returnUrl", "http://localhost:8086/pay/redirect");
            }

            log.info("[USER→PAY] {}", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String payUrl = "http://payment-service/api/payments/pay";
            ResponseEntity<String> payRes = restTemplate.exchange(payUrl, HttpMethod.POST, entity, String.class);
            return payRes.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Thanh toán thất bại ({}): {}", e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            return "Payment failed: " + e.getStatusCode().value() + " " + e.getStatusText();
        } catch (Exception e) {
            logger.error("Thanh toán thất bại: {}", e.getMessage(), e);
            return "Payment failed due to error";
        }
    }

    public byte[] exportProductsExcelBytes(HttpServletRequest request) {
        HttpHeaders headers = createAuthHeaders(request);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(
                "http://product-service/api/products/excel/export",
                HttpMethod.GET, entity, byte[].class);
        return resp.getBody();
    }
    public byte[] templateProductsExcelBytes(HttpServletRequest request) {
        HttpHeaders headers = createAuthHeaders(request);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<byte[]> resp = restTemplate.exchange(
                "http://product-service/api/products/excel/template",
                HttpMethod.GET, entity, byte[].class);
        return resp.getBody();
    }
    public String importProductsExcelJson(MultipartFile file, HttpServletRequest request) {
        try {
            HttpHeaders headersAuth = createAuthHeaders(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (headersAuth.getFirst(HttpHeaders.AUTHORIZATION) != null) {
                headers.set(HttpHeaders.AUTHORIZATION, headersAuth.getFirst(HttpHeaders.AUTHORIZATION));
            }

            ByteArrayResource fileRes = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileRes);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.exchange(
                    "http://product-service/api/products/excel/import",
                    HttpMethod.POST, entity, String.class);

            return resp.getBody();
        } catch (Exception e) {
            log.error("Import excel failed", e);
            return "{\"created\":0,\"updated\":0,\"failed\":1,\"warnings\":[\"Import failed: "
                    + e.getMessage().replace("\"", "'") + "\"]}";
        }
    }
}
