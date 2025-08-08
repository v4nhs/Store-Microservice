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
    public boolean updateQuantity(String productId, int quantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            System.out.println("Không tìm thấy productId: " + productId);
            return false;
        }

        Product product = productOpt.get();
        int oldQuantity = product.getQuantity();
        product.setQuantity(quantity);
        productRepository.save(product);

        System.out.println("Đã cập nhật quantity cho productId: " + productId +
                " | Old Qty: " + oldQuantity + " -> New Qty: " + quantity);
        return true;
    }
}
