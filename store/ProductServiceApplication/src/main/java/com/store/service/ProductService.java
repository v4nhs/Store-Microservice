package com.store.service;

import com.store.dto.ProductUpdateRequest;
import com.store.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public interface ProductService {
    Product save(Product product);
    List<Product> getAll();
    void deleteById(String id);
    Optional<Product> findById(String id);
    Product updateProduct(String id, ProductUpdateRequest updatedProduct);
}
