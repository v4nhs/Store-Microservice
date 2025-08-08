package service;

import com.store.dto.ProductCreatedEvent;
import com.store.model.Product;
import com.store.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductCreatedEvent createProduct(ProductCreatedEvent dto) {
        Product product = new Product();
        product.setId(dto.getId());
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());

        productRepository.save(product);
        return dto;
    }
}
