package com.store.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.ProductCreatedEvent;
import com.store.dto.ProductDTO;
import com.store.dto.ProductUpdateEvent;
import com.store.model.Product;
import com.store.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setQuantity(dto.getQuantity());

        Product saved = productRepository.save(product);

        ProductCreatedEvent event = new ProductCreatedEvent();
        event.setProductId(saved.getId());
        event.setName(saved.getName());
        event.setPrice(saved.getPrice());
        event.setQuantity(saved.getQuantity());
        sendJson("product-created-topic", event);

        return new ProductDTO(saved.getId(), saved.getName(), saved.getPrice(), saved.getQuantity());
    }


    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(product -> new ProductDTO(
                        product.getId(),
                        product.getName(),
                        product.getPrice(),
                        product.getQuantity()
                ))
                .toList();
    }

    public ProductDTO getProductById(String id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        return new ProductDTO(
                p.getId(),
                p.getName(),
                p.getPrice(),
                p.getQuantity()
        );
    }

    @Transactional
    public ProductDTO updateProduct(String id, ProductDTO dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setQuantity(dto.getQuantity());
        Product saved = productRepository.save(product);

        // publish UPDATED using ProductUpdateEvent
        ProductUpdateEvent evt = new ProductUpdateEvent(
                saved.getId(), saved.getName(), saved.getPrice(), saved.getQuantity()
        );
        sendJson("product-updated-topic", evt);

        return new ProductDTO(saved.getId(), saved.getName(), saved.getPrice(), saved.getQuantity());
    }

    @Transactional
    public void updateQuantity(String productId, int newQuantity) {
        if (newQuantity < 0) throw new IllegalArgumentException("newQuantity không được âm");
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        int old = p.getQuantity();
        p.setQuantity(newQuantity);
        Product saved = productRepository.save(p);
        log.info("Admin updated quantity for productId={} | {} -> {}", productId, old, newQuantity);

        // publish UPDATED using ProductUpdateEvent (để Inventory sync)
        ProductUpdateEvent evt = new ProductUpdateEvent(
                saved.getId(), saved.getName(), saved.getPrice(), saved.getQuantity()
        );
        sendJson("product-updated-topic", evt);
    }
    @Transactional
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
        // publish deleted (chỉ cần productId)
        sendJson("product-deleted-topic", new ProductCreatedEvent(id, null, 0.0, 0));
    }

    private ProductCreatedEvent mapToDto(Product product) {
        return new ProductCreatedEvent(product.getId(), product.getName(), product.getPrice(), product.getQuantity());
    }

    private void sendJson(String topic, Object payload) {
        try {
            String json = om.writeValueAsString(payload);
            log.info("Publish {} => {}", topic, json);
            kafkaTemplate.send(topic, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
