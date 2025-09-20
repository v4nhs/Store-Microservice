package com.store.service;

import com.store.client.ProductClientRest;
import com.store.dto.ProductDTO;
import com.store.keyword.KeywordExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductClientRest client;

    public Optional<ProductDTO> find(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();

        //Chuẩn hoá tên
        String name = normalizeProductName(raw);

        if (name.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) {
            var p = client.getById(name);
            return Optional.ofNullable(p);
        }
        var p = client.getByName(name);
        return Optional.ofNullable(p);
    }

    // ---- helpers ----
    private static String normalizeProductName(String q) {
        String s = q.trim();

        // bỏ các tiền tố hay gặp
        s = s.replaceFirst("(?i)^(hãy|vui lòng|cho tôi|xin)\\s+", "");
        s = s.replaceFirst("(?i)^(ảnh|hình|image|img|photo)(\\s+của)?\\s+", "");
        s = s.replaceFirst("(?i)^(giá|gia|tồn|ton|còn bao nhiêu|con bao nhieu|số lượng|sl)\\s+(của|cua)?\\s*", "");
        s = s.replaceFirst("(?i)^(sản phẩm|san pham)\\s+", "");
        s = s.replaceFirst("(?i)^(id|ID)(\\s+của)?\\s+", "");
        s = s.replaceAll("(?i)\\b(là\\s*bao\\s*nh(i|í)u|bao\\s*nh(i|í)u|là\\s*bao\\s*nhieu|bao\\s*nhieu)\\b", "");
        s = s.replaceAll("(?i)\\b(là\\s*bao\\s*nh(i|í)u|bao\\s*nh(i|í)u|bao nhiêu tiền)\\b", "");
        s = s.replaceAll("[\"'.,!?;:()\\[\\]]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
    @Cacheable(cacheNames = "productById", key = "#id", unless = "#result == null")
    public ProductDTO getById(String id) { return client.getById(id); }

    @Cacheable(cacheNames = "productByName",
            key = "T(org.springframework.util.StringUtils).trimAllWhitespace(#name.toLowerCase())",
            unless = "#result == null")
    public ProductDTO getByName(String name) { return client.getByName(name); }

    @CacheEvict(cacheNames = {"productById"}, key = "#id") public void evictById(String id) {}
    @CacheEvict(cacheNames = {"productByName"},
            key = "T(org.springframework.util.StringUtils).trimAllWhitespace(#name.toLowerCase())")
    public void evictByName(String name) {}

    @CachePut(cacheNames = "productById", key = "#p.id") public ProductDTO refreshById(ProductDTO p){ return p; }
    @CachePut(cacheNames = "productByName",
            key = "T(org.springframework.util.StringUtils).trimAllWhitespace(#p.name.toLowerCase())")
    public ProductDTO refreshByName(ProductDTO p){ return p; }
    public Optional<ProductDTO> smartFind(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();

        // 1) Nếu là UUID → lấy theo ID (cache productById)
        String trimmed = raw.trim();
        if (trimmed.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) {
            return Optional.ofNullable(getById(trimmed));
        }

        // 2) Lấy "best phrase" (đã loại stopword, bỏ dấu, chuẩn hóa) → hỏi theo tên (cache productByName)
        String best = KeywordExtractor.bestPhrase(raw);
        if (best != null && !best.isBlank()) {
            ProductDTO byBest = getByName(best);
            if (byBest != null) return Optional.of(byBest);
        }

        // 3) Thử lần lượt tất cả các phrase (3-gram/2-gram/1-gram) cho đến khi thấy
        var kw = KeywordExtractor.extract(raw);
        for (String phrase : kw.phrases()) {
            ProductDTO p = getByName(phrase);
            if (p != null) return Optional.of(p);
        }

        // 4) Cuối cùng, fallback: normalize nhẹ nhàng như luồng find(...) cũ
        return find(raw);
    }
}
