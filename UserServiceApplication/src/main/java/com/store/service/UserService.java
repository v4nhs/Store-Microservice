package com.store.service;


import com.store.dto.OrderDTO;
import com.store.dto.PaymentRequest;
import com.store.dto.ProductDTO;
import com.store.dto.UserDTO;
import com.store.model.Order;
import com.store.model.User;
import com.store.repository.UserRepository;
import com.store.request.OrderRequest;
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
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
            String token = request.getHeader("Authorization"); // Lấy token từ request thay vì dto
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

    public String checkout(OrderRequest orderRequest, HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Claims claims = Jwts.parser()
                    .setSigningKey("Y3ZndQGRXwfnr+Ub6sCBDkri7Z1z8refHJYaaO42OZnyh1d70pHAV7it+bh/81rM")
                    .parseClaimsJws(token)
                    .getBody();
            String userId = claims.get("userId", String.class);

            // 1. Gửi request thanh toán sang payment-service
            PaymentRequest paymentRequest = new PaymentRequest();
            paymentRequest.setUserId(userId);
            paymentRequest.setAmount(orderRequest.getQuantity() * 100.0); // ví dụ đơn giản

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<PaymentRequest> paymentEntity = new HttpEntity<>(paymentRequest, headers);
            ResponseEntity<String> paymentResponse = restTemplate.exchange(
                    "http://payment-service/api/payments",
                    HttpMethod.POST,
                    paymentEntity,
                    String.class
            );

            if (paymentResponse.getStatusCode() == HttpStatus.OK) {
                // 2. Nếu thanh toán thành công thì tạo order
                orderRequest.setUserId(userId);
                HttpEntity<OrderRequest> orderEntity = new HttpEntity<>(orderRequest, headers);

                ResponseEntity<Order> orderResponse = restTemplate.exchange(
                        "http://order-service/api/orders",
                        HttpMethod.POST,
                        orderEntity,
                        Order.class
                );

                Order order = orderResponse.getBody();
                return order != null ? order.toString() : "Order creation failed";
            } else {
                return "Payment failed, order not created";
            }

        } catch (Exception e) {
            logger.error("Checkout thất bại: {}", e.getMessage(), e);
            return "Checkout failed due to internal error";
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