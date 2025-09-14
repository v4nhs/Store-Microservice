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

    // === Helpers ===
    /** Map sizes entity -> List<String> (cho event) */
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

    /** Map sizes entity -> List<Map<String,Object>> (size + quantity) cho event */
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
    /** Nhận danh sách DTO và tạo entity ProductSize, gắn 2 chiều với product */
    private static List<ProductSize> attachSizes(Product product, List<ProductSizeDTO> sizeDtos) {
        if (sizeDtos == null) return List.of();
        List<ProductSize> result = new ArrayList<>();
        for (ProductSizeDTO d : sizeDtos) {
            if (d == null || d.getSize() == null || d.getSize().isBlank()) continue;
            ProductSize s = new ProductSize();
            s.setSize(d.getSize().trim());
            s.setQuantity(d.getQuantity() == null ? 0 : Math.max(0, d.getQuantity()));
            s.setProduct(product);             // gắn 2 chiều
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
        System.out.println("[DBG] after setSizes quantity=" + product.getQuantity());
        product.setPrice(dto.getPrice());
        product.recalcQuantityFromSizes();

        Product saved = productRepository.save(product);
        System.out.println("[DBG] after save quantity=" + saved.getQuantity());
        productRepository.flush();
        log.info("[DBG] after save+flush quantity={}", saved.getQuantity());
        Product loaded = productRepository.findById(saved.getId()).get();
        System.out.println("[DBG] after reload quantity=" + loaded.getQuantity());
        Map<String,Object> event = Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "image", saved.getImage(),
                "price", saved.getPrice(),
                "sizes", toSizeStrings(saved.getSizes()),
                "sizesWithQty", toSizeObjects(saved.getSizes()),
                "quantity", saved.getQuantity()
        );
        sendJson("product-created-topic", event);

        return new ProductDTO(saved.getId(),
                saved.getName(),
                saved.getImage(),
                toSizeDTOs(saved.getSizes()),
                saved.getPrice(),
                saved.getQuantity());
    }

    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
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

    @Transactional
    public ProductDTO updateProduct(String id, ProductDTO dto) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // 1) Cập nhật thông tin cơ bản
        p.setName(dto.getName());
        p.setImage(dto.getImage());
        p.setPrice(dto.getPrice());

        // 2) Dedup payload theo size
        List<ProductSizeDTO> incomingList = dto.getSizes() == null ? List.of() : dto.getSizes();
        Map<String, ProductSizeDTO> incoming = incomingList.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getSize() != null && !s.getSize().isBlank())
                .collect(Collectors.toMap(
                        ProductSizeDTO::getSize,
                        Function.identity(),
                        (a, b) -> b
                ));

        // 3) Map hiện trạng theo size
        Map<String, ProductSize> existing = (p.getSizes() == null ? List.<ProductSize>of() : p.getSizes()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ProductSize::getSize,
                        Function.identity(),
                        (a, b) -> a
                ));

        // 4) Xoá size không còn trong payload (DELETE trước INSERT)
        boolean removed = p.getSizes().removeIf(s -> !incoming.containsKey(s.getSize()));
        if (removed) {
            productRepository.flush();
        }

        // 5) UPSERT size
        for (var e : incoming.entrySet()) {
            String sizeKey = e.getKey();
            Integer qty = e.getValue().getQuantity();
            int safeQty = Math.max(0, qty == null ? 0 : qty);

            ProductSize current = existing.get(sizeKey);
            if (current != null) {
                current.setQuantity(safeQty);
            } else {
                ProductSize ns = new ProductSize();
                ns.setSize(sizeKey);
                ns.setQuantity(safeQty);
                ns.setProduct(p);
                p.getSizes().add(ns);
            }
        }

        // 6) Tính lại tổng
        p.recalcQuantityFromSizes();

        // 7) Lưu & flush
        Product saved = productRepository.saveAndFlush(p);

        // 8) Publish event
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", saved.getId());
        event.put("name", saved.getName());
        event.put("image", saved.getImage());
        event.put("price", saved.getPrice());
        event.put("quantity", saved.getQuantity());
        event.put("sizes", saved.getSizes().stream().map(ProductSize::getSize).toList());
        event.put("sizesWithQty", saved.getSizes().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("size", s.getSize());
            m.put("quantity", s.getQuantity() == null ? 0 : s.getQuantity());
            return m;
        }).toList());
        sendJson("product-updated-topic", event);

        // 9) Trả về DTO từ entity đã lưu (map List<ProductSize> -> List<ProductSizeDTO>)
        List<ProductSizeDTO> sizeDtos = toSizeDTOs(saved.getSizes());
        return new ProductDTO(
                saved.getId(),
                saved.getName(),
                saved.getImage(),
                sizeDtos,
                saved.getPrice(),
                saved.getQuantity()
        );
    }

    /** Helper: map entity -> DTO */
    private static List<ProductSizeDTO> toSizeDTOs(List<ProductSize> sizes) {
        if (sizes == null) return List.of();
        return sizes.stream()
                .map(s -> new ProductSizeDTO(s.getSize(), s.getQuantity()))
                .collect(Collectors.toList());
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


    private void sendJson(String topic, Object payload) {
        try {
            String json = om.writeValueAsString(payload);
            log.info("Publish {} => {}", topic, json);
            kafkaTemplate.send(topic, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
