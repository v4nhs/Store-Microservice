package com.store.controller;

import com.store.dto.ProductDto;
import com.store.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ProductDto create(@RequestBody ProductDto dto) {
        return productService.createProduct(dto);
    }

    @GetMapping
    public List<ProductDto> getAll() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable String id) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable String id, @RequestBody ProductDto dto) {
        return productService.updateProduct(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        productService.deleteProduct(id);
    }
}