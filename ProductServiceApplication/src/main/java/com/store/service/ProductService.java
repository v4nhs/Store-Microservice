package com.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.ProductCreatedEvent;
import com.store.dto.ProductDTO;
import com.store.dto.ProductSizeDTO;
import com.store.dto.ProductUpdateEvent;
import com.store.model.Product;
import com.store.model.ProductSize;
import com.store.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper om = new ObjectMapper();

    private static List<String> toSizeStrings(List<ProductSize> sizes) {
        if (sizes == null) return List.of();
        return sizes.stream()
                .filter(Objects::nonNull)
                .map(ProductSize::getSize)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static List<Map<String, Object>> toSizeObjects(List<ProductSize> sizes) {
        if (sizes == null) return List.of();
        return sizes.stream()
                .filter(Objects::nonNull)
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("size", s.getSize());
                    m.put("quantity", s.getQuantity() == null ? 0 : s.getQuantity());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private static List<ProductSize> attachSizes(Product product, List<ProductSizeDTO> sizeDtos) {
        if (sizeDtos == null) return List.of();
        List<ProductSize> result = new ArrayList<>();
        for (ProductSizeDTO d : sizeDtos) {
            if (d == null || d.getSize() == null || d.getSize().isBlank()) continue;
            ProductSize s = new ProductSize();
            s.setSize(d.getSize().trim());
            s.setQuantity(d.getQuantity() == null ? 0 : Math.max(0, d.getQuantity()));
            s.setProduct(product);
            result.add(s);
        }
        return result;
    }

    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        Product product = new Product();
        product.setName(dto.getName());
        product.setImage(dto.getImage());
        product.setSizes(attachSizes(product, dto.getSizes()));
        product.setPrice(dto.getPrice());
        product.recalcQuantityFromSizes();

        Product saved = productRepository.saveAndFlush(product);

        sendJson("product-created-topic", Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "image", saved.getImage(),
                "price", saved.getPrice(),
                "sizes", toSizeStrings(saved.getSizes()),
                "sizesWithQty", toSizeObjects(saved.getSizes()),
                "quantity", saved.getQuantity()
        ));

        return new ProductDTO(saved.getId(),
                saved.getName(),
                saved.getImage(),
                toSizeDTOs(saved.getSizes()),
                saved.getPrice(),
                saved.getQuantity());
    }

    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(product -> new ProductDTO(
                        product.getId(),
                        product.getName(),
                        product.getImage(),
                        toSizeDTOs(product.getSizes()),
                        product.getPrice(),
                        product.getQuantity()
                ))
                .toList();
    }

    public ProductDTO getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getImage(),
                toSizeDTOs(product.getSizes()),
                product.getPrice(),
                product.getQuantity()
        );
    }
    public Optional<ProductDTO> getByName(String name) {
        String q = (name == null) ? "" : name.trim();
        if (q.isEmpty()) return Optional.empty();
        Optional<Product> hit = productRepository.findTop1ByNameIgnoreCase(q);
        if (hit.isEmpty()) {
            hit = productRepository.findTop1ByNameContainingIgnoreCase(q);
        }
        if (hit.isEmpty()) {
            try {
                hit = productRepository.findOneAiCiLike(q);
            } catch (Exception ignore) {
            }
        }
        return hit.map(this::toDto);
    }
    @Transactional
    public ProductDTO updateProduct(String id, ProductDTO dto) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        p.setName(dto.getName());
        p.setImage(dto.getImage());
        p.setPrice(dto.getPrice());

        Map<String, ProductSizeDTO> incoming = Optional.ofNullable(dto.getSizes())
                .orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getSize() != null && !s.getSize().isBlank())
                .collect(Collectors.toMap(
                        s -> s.getSize().trim(),
                        Function.identity(),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        p.getSizes().removeIf(s -> !incoming.containsKey(s.getSize()));
        productRepository.flush();

        for (var e : incoming.entrySet()) {
            String size = e.getKey();
            int qty = Math.max(0, Optional.ofNullable(e.getValue().getQuantity()).orElse(0));
            ProductSize existing = p.getSizes().stream()
                    .filter(s -> size.equals(s.getSize()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.setQuantity(qty);
            } else {
                ProductSize ns = new ProductSize();
                ns.setSize(size);
                ns.setQuantity(qty);
                ns.setProduct(p);
                p.getSizes().add(ns);
            }
        }

        p.recalcQuantityFromSizes();
        Product saved = productRepository.saveAndFlush(p);

        sendJson("product-updated-topic", Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "image", saved.getImage(),
                "price", saved.getPrice(),
                "quantity", saved.getQuantity(),
                "sizes", toSizeStrings(saved.getSizes()),
                "sizesWithQty", toSizeObjects(saved.getSizes())
        ));

        return new ProductDTO(saved.getId(),
                saved.getName(),
                saved.getImage(),
                toSizeDTOs(saved.getSizes()),
                saved.getPrice(),
                saved.getQuantity());
    }
    @Transactional
    public void updateQuantity(String productId, int newQuantity) {
        if (newQuantity < 0) throw new IllegalArgumentException("newQuantity không được âm");
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));
        int old = p.getQuantity();
        p.setQuantity(newQuantity);
        Product saved = productRepository.save(p);
        log.info("Admin updated quantity for productId={} | {} -> {}", productId, old, newQuantity);

        Map<String,Object> event = Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "image", saved.getImage(),
                "price", saved.getPrice(),
                "quantity", saved.getQuantity(),
                "sizes", toSizeStrings(saved.getSizes()),
                "sizesWithQty", toSizeObjects(saved.getSizes())
        );

        sendJson("product-updated-topic", event);
    }

    @Transactional
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
        Map<String,Object> ev = Map.of("id", id, "deleted", true);
        sendJson("product-deleted-topic", ev);
    }
    private static List<ProductSizeDTO> toSizeDTOs(List<ProductSize> sizes) {
        if (sizes == null) return List.of();
        return sizes.stream()
                .map(s -> new ProductSizeDTO(s.getSize(), s.getQuantity()))
                .toList();
    }
    private ProductDTO toDto(Product p) {
        return new ProductDTO(
                p.getId(),
                p.getName(),
                p.getImage(),
                toSizeDTOs(p.getSizes()),
                p.getPrice(),
                p.getQuantity()
        );
    }
    private void sendJson(String topic, Object payload) {
        try {
            kafkaTemplate.send(topic, om.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
