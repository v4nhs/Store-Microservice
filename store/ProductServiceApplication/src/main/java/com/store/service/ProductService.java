package com.store.service;


import com.store.dto.ProductDto;
import com.store.model.Product;
import com.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductDto createProduct(ProductDto dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setQuantity(dto.getQuantity());
        Product saved = productRepository.save(product);
        return mapToDto(saved);
    }

    public List<ProductDto> getAllProducts() {
        return productRepository.findAll().stream().map(this::mapToDto).toList();
    }

    public ProductDto getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return mapToDto(product);
    }

    public ProductDto updateProduct(String id, ProductDto dto) {
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

    private ProductDto mapToDto(Product product) {
        return new ProductDto(product.getId(), product.getName(), product.getPrice(), product.getQuantity());
    }
}
