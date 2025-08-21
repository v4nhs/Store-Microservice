package com.store.service;


import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import com.store.dto.UserDTO;
import com.store.model.Order;
import com.store.model.User;
import com.store.repository.UserRepository;
import com.store.dto.request.OrderRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            if (!token.startsWith("Bearer ")) {
                token = "Bearer " + token;
            }
            headers.set("Authorization", token);
        }
        return headers;
    }
    public ProductDTO createProduct(ProductDTO productDto, HttpServletRequest request) {
        try {
            logger.info("Authorization token from request: {}", request.getHeader("Authorization"));
            HttpHeaders headers = createAuthHeaders(request);
            HttpEntity<ProductDTO> httpEntity = new HttpEntity<>(productDto, headers);

            ResponseEntity<ProductDTO> response = restTemplate.postForEntity(
                    "http://product-service/api/products",
                    httpEntity,
                    ProductDTO.class
            );

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
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
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

    public String placeOrder(OrderDTO dto, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // Giải mã token để lấy userId
            Claims claims = Jwts.parser()
                    .setSigningKey("Y3ZndQGRXwfnr+Ub6sCBDkri7Z1z8refHJYaaO42OZnyh1d70pHAV7it+bh/81rM")
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.get("userId", String.class);
            logger.info("userId from token: {}", userId);

            // 🌟 Map từ DTO sang Request
            OrderRequest orderRequest = new OrderRequest();
            orderRequest.setUserId(userId);
            orderRequest.setProductId(dto.getProductId());
            orderRequest.setQuantity(dto.getQuantity());

            // Tạo header và entity để gửi request
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<OrderRequest> entity = new HttpEntity<>(orderRequest, headers);
            logger.info("Đang gửi request tới order-service...");
            // Gửi POST request đến order-service
            ResponseEntity<Order> response = restTemplate.exchange(
                    "http://order-service/api/orders",
                    HttpMethod.POST,
                    entity,
                    Order.class
            );

            Order orderResponse = response.getBody();
            logger.info("Order nhận về: {}", orderResponse);
            logger.info("Status: {}", response.getStatusCodeValue());
            logger.info("Body: {}", response.getBody());
            logger.info("Đang gửi request tới order-service...");
            return orderResponse != null ? orderResponse.toString() : "Order creation failed";


        } catch (Exception e) {
            logger.error("Đặt hàng thất bại: {}", e.getMessage(), e);
            return "Order creation failed due to internal error";
        }
    }

    public String placeOrderMulti(List<OrderDTO> items, HttpServletRequest request) {
        try {
            if (items == null || items.isEmpty()) {
                return "Danh sách sản phẩm trống";
            }

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
            } catch (Exception ignore) {

            }

            MultiOrderRequest payload = new MultiOrderRequest();
            payload.setUserId(userId);
            List<MultiOrderRequest.Item> reqItems = new ArrayList<>();
            for (OrderDTO dto : items) {
                if (dto.getProductId() == null || dto.getProductId().isBlank() || dto.getQuantity() == null || dto.getQuantity() < 1) {
                    return "Sản phẩm không hợp lệ (productId/quantity)";
                }
                MultiOrderRequest.Item it = new MultiOrderRequest.Item();
                it.setProductId(dto.getProductId());
                it.setQuantity(dto.getQuantity());
                reqItems.add(it);
            }
            payload.setItems(reqItems);

            HttpHeaders headers = createAuthHeaders(request); // forward Authorization
            HttpEntity<MultiOrderRequest> entity = new HttpEntity<>(payload, headers);

            logger.info("Đang gửi request tới order-service (multi) với {} items...", reqItems.size());
            ResponseEntity<Order> response = restTemplate.exchange(
                    "http://order-service/api/orders/multi",
                    HttpMethod.POST,
                    entity,
                    Order.class
            );

            Order order = response.getBody();
            if (order == null) return "Order creation failed";

            BigDecimal total = order.getTotalAmount();
            return "Created order id=" + order.getId() + ", status=" + order.getStatus() + ", total=" + (total != null ? total : BigDecimal.ZERO);
        } catch (HttpClientErrorException e) {
            logger.error("Đặt hàng (multi) thất bại ({}): {}", e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            return "Order creation failed: " + e.getStatusCode().value() + " " + e.getStatusText();
        } catch (Exception e) {
            logger.error("Đặt hàng (multi) thất bại: {}", e.getMessage(), e);
            return "Order creation failed due to error";
        }
    }

    // Payload tối thiểu khớp với /api/orders/multi của order-service
    public static class MultiOrderRequest {
        private String userId;
        private List<Item> items;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public List<Item> getItems() { return items; }
        public void setItems(List<Item> items) { this.items = items; }

        public static class Item {
            private String productId;
            private Integer quantity;

            public String getProductId() { return productId; }
            public void setProductId(String productId) { this.productId = productId; }
            public Integer getQuantity() { return quantity; }
            public void setQuantity(Integer quantity) { this.quantity = quantity; }
        }
    }

    public List<OrderDTO> getAllOrder() {
        ResponseEntity<List<OrderDTO>> response = restTemplate.exchange(
                "http://order-service/api/orders",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public OrderDTO getOrderById(String id) {
        return restTemplate.getForObject("http://order-service/api/orders/" + id, OrderDTO.class);
    }

    public String payOrder(String orderId, String idempotencyKey, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isBlank()) headers.setBearerAuth(token);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) headers.set("Idempotency-Key", idempotencyKey);

            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("amount", null);                      // để payment-service tự lấy từ order-service
            body.put("idempotencyKey", idempotencyKey);
            body.put("method", "MOMO");                    // 👈 THÊM
            body.put("provider", "MOMO");                  // 👈 tuỳ chọn
            body.put("returnUrl", "http://localhost:8086/pay/redirect"); // 👈 tuỳ chọn

            // log để soi payload gửi đi
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