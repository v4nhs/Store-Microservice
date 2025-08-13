package com.store.controller;

import com.store.dto.ProductCreatedEvent;
import com.store.dto.ProductDeletedEvent;
import com.store.dto.ProductResponse;
import com.store.repository.ProductRepository;
import com.store.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final KafkaTemplate kafkaTemplate;

    @PostMapping
    public ProductResponse create(@RequestBody ProductResponse dto) {
        return productService.createProduct(dto);
    }

    @GetMapping
    public List<ProductCreatedEvent> getAll() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ProductCreatedEvent getById(@PathVariable String id) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    public ProductCreatedEvent update(@PathVariable String id, @RequestBody ProductCreatedEvent dto) {
        return productService.updateProduct(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!productRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        productRepository.deleteById(id);
        kafkaTemplate.send("product-deleted-topic", new ProductDeletedEvent(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stock/{productId}")
    public ResponseEntity<Integer> getProductStock(@PathVariable String productId) {
        ProductCreatedEvent product = productService.getProductById(productId);
        return ResponseEntity.ok(product.getQuantity());
    }

    @PutMapping("/{productId}/quantity")
    public ResponseEntity<Void> updateQuantity(@PathVariable String productId, @RequestParam int quantity) {
        productService.updateQuantity(productId, quantity);
        return ResponseEntity.ok().build();
    }
}