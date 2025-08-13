package com.store.service;


import com.store.dto.ProductCreatedEvent;
import com.store.dto.ProductResponse;
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

    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;
    @Transactional
    public ProductResponse createProduct(ProductResponse dto) {
        log.info("1");
        Product product = new Product();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setQuantity(dto.getQuantity());

        Product saved = productRepository.save(product);

        System.out.println(saved);

        ProductCreatedEvent event = new ProductCreatedEvent();
        event.setProductId(saved.getId());
        event.setQuantity(saved.getQuantity());
        log.info("Sending event to Kafka: {}", event);
        kafkaTemplate.send("product-created-topic", event);

        return new ProductResponse(saved.getId(), saved.getName(), saved.getPrice(), saved.getQuantity());
    }


    public List<ProductCreatedEvent> getAllProducts() {
        return productRepository.findAll().stream().map(this::mapToDto).toList();
    }

    public ProductCreatedEvent getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToDto(product);
    }

    public ProductCreatedEvent updateProduct(String id, ProductCreatedEvent dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setQuantity(dto.getQuantity());

        return mapToDto(productRepository.save(product));
    }

    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }

    private ProductCreatedEvent mapToDto(Product product) {
        return new ProductCreatedEvent(product.getId(), product.getName(), product.getPrice(), product.getQuantity());
    }

    @Transactional
    public void updateQuantity(String productId, int newQuantity) {
        if (newQuantity < 0) throw new IllegalArgumentException("newQuantity không được âm");
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        int old = p.getQuantity();
        p.setQuantity(newQuantity);
        productRepository.save(p);
        log.info("Admin updated quantity for productId={} | {} -> {}", productId, old, newQuantity);
    }
}
