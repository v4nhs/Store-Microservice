package com.store.controller;

import com.store.dto.ProductCreatedEvent;
import com.store.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ProductCreatedEvent create(@RequestBody ProductCreatedEvent dto) {
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
    public void delete(@PathVariable String id) {
        productService.deleteProduct(id);
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