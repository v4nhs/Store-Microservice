package com.store.service;


import com.store.dto.ProductCreatedEvent;
import com.store.model.Product;
import com.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
