package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import com.store.dto.UserDTO;
import com.store.model.Order;
import com.store.repository.OrderRepository;
import com.store.request.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(OrderRequest request) {
        log.info("============= CREATE ORDER (SAGA) =============");

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("PENDING");

        Order saved = orderRepository.save(order);
        log.info("Đã lưu Order PENDING với ID: {}", saved.getId());

        // 2) Sau khi commit DB, publish OrderDTO đã enrich
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    String productName = null;
                    double price = 0.0;
                    try {
                        ResponseEntity<ProductDTO> resp = restTemplate.getForEntity(
                                "http://product-service/api/products/" + saved.getProductId(),
                                ProductDTO.class
                        );
                        ProductDTO p = resp.getBody();
                        if (p != null) {
                            productName = p.getName();
                            price = p.getPrice();
                        }
                    } catch (Exception ex) {
                        log.warn("Không lấy được sản phẩm {}: {}", saved.getProductId(), ex.getMessage());
                    }

                    String email = null;
                    try {
                        ResponseEntity<UserDTO> resp = restTemplate.getForEntity(
                                "http://user-service/api/users/" + saved.getUserId(),
                                UserDTO.class
                        );
                        UserDTO u = resp.getBody();
                        if (u != null) {
                            email = u.getEmail();
                        }
                    } catch (Exception ex) {
                        log.warn("Không lấy được user {}: {}", saved.getUserId(), ex.getMessage());
                    }

                    OrderDTO evt = OrderDTO.builder()
                            .orderId(saved.getId())
                            .userId(saved.getUserId())
                            .productId(saved.getProductId())
                            .productName(productName)
                            .price(price)
                            .quantity(saved.getQuantity())
                            .status(saved.getStatus() != null ? saved.getStatus() : "PENDING")
                            .email(email)
                            .build();

                    kafkaTemplate.send("order-created", evt);
                    log.info("Đã publish 'order-created' (enrich) cho {}", saved.getId());
                } catch (Exception ex) {
                    log.error("Publish 'order-created' thất bại cho {}: {}", saved.getId(), ex.getMessage(), ex);
                }
            }
        });

        return saved;
    }
}