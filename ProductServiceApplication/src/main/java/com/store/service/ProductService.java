package com.store.service;


import com.store.dto.ProductCreatedEvent;
import com.store.model.Product;
import com.store.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    public ProductCreatedEvent createProduct(ProductCreatedEvent dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setQuantity(dto.getQuantity());

        Product saved = productRepository.save(product);

        // Gửi sự kiện sang Kafka
        ProductCreatedEvent event = new ProductCreatedEvent();
        event.setProductId(saved.getId());
        event.setQuantity(saved.getQuantity());
        log.info("Sending event to Kafka: {}", event);
        kafkaTemplate.send("product-created-topic", event);


        return mapToDto(saved);
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
    public boolean reserveStock(String productId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty phải > 0");
        int updated = productRepository.reserve(productId, qty);
        return updated == 1;
    }


    @Transactional
    public void releaseStock(String productId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty phải > 0");
        productRepository.release(productId, qty);
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
