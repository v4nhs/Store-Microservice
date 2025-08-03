package com.store.controller;

import com.google.common.net.HttpHeaders;
import com.store.dto.ProductUpdateRequest;
import com.store.model.Product;
import com.store.repository.ProductRepository;
import com.store.request.InventoryRequest;
import com.store.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final RestTemplate restTemplate;
    private final JwtUtil jwtUtil;

    private final String INVENTORY_SERVICE_URL = "http://localhost:8083/inventory";

    @GetMapping
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable String id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProductUpdateRequest productRequest, HttpServletRequest request) {
        String username = extractUsernameFromToken(request);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        Product product = new Product();
        product.setName(productRequest.getName());
        product.setImage(productRequest.getImage());
        product.setKeyword(productRequest.getKeyword());
        product.setPrice(productRequest.getPrice());
        product.setCreatedBy(username);

        Product saved = productRepository.save(product);

        InventoryRequest inventory = new InventoryRequest(saved.getId(), productRequest.getQuantity());
        try {
            restTemplate.postForObject(INVENTORY_SERVICE_URL, inventory, Void.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Product created, but failed to create inventory");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                    @RequestBody ProductUpdateRequest productRequest,
                                    HttpServletRequest request) {
        String username = extractUsernameFromToken(request);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        return productRepository.findById(id)
                .map(existing -> {
                    existing.setName(productRequest.getName());
                    existing.setImage(productRequest.getImage());
                    existing.setKeyword(productRequest.getKeyword());
                    existing.setPrice(productRequest.getPrice());
                    Product updated = productRepository.save(existing);

                    InventoryRequest inventory = new InventoryRequest(updated.getId(), productRequest.getQuantity());

                    try {
                        restTemplate.put(INVENTORY_SERVICE_URL + "/" + updated.getId(), inventory);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Product updated, but failed to update inventory");
                    }

                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        return productRepository.findById(id)
                .map(p -> {
                    productRepository.delete(p);
                    try {
                        restTemplate.delete(INVENTORY_SERVICE_URL + "/" + id);
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Product deleted, but failed to delete inventory");
                    }
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String extractUsernameFromToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            return jwtUtil.getUsernameFromToken(token);
        }
        return null;
    }
}

