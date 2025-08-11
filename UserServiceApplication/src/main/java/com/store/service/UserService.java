package com.store.service;


import com.store.dto.OrderDto;
import com.store.dto.ProductDto;
import com.store.dto.UserDto;
import com.store.model.Order;
import com.store.model.User;
import com.store.repository.UserRepository;
import com.store.request.OrderRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;

    @Cacheable(value = "allUsers", key = "'all'")
    public List<User> getAllUser(){
        System.out.println("Get all User first on.....");
        return userRepository.findAll();
    }
    public UserDto getUserByUsername(String username) {
        String url = "http://user-service/api/users/" + username;
        return restTemplate.getForObject(url, UserDto.class);
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
    public ProductDto createProduct(ProductDto productDto, HttpServletRequest request) {
        try {
            logger.info("Authorization token from request: {}", request.getHeader("Authorization"));
            HttpHeaders headers = createAuthHeaders(request);
            HttpEntity<ProductDto> httpEntity = new HttpEntity<>(productDto, headers);

            ResponseEntity<ProductDto> response = restTemplate.postForEntity(
                    "http://product-service/api/products",
                    httpEntity,
                    ProductDto.class
            );

            logger.info("Headers to send: {}", headers);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Lỗi tạo sản phẩm: {}", e.getMessage(), e);
            return null;
        }
    }
    public List<ProductDto> getAllProducts() {
        ResponseEntity<List<ProductDto>> response = restTemplate.exchange(
                "http://product-service/api/products",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    public ProductDto getProductById(String id) {
        return restTemplate.getForObject("http://product-service/api/products/" + id, ProductDto.class);
    }

    public void deleteProduct(String id) {
        restTemplate.delete("http://product-service/api/products/" + id);
    }

    public ProductDto updateProduct(String id, ProductDto dto) {
        restTemplate.put("http://product-service/api/products/" + id, dto);
        return getProductById(id);
    }

    public String placeOrder(OrderDto dto, HttpServletRequest request) {
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
}