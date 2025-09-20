package com.store.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRagConsumer {

    private final ObjectMapper objectMapper;
    private final RagService rag;

    @KafkaListener(
            topics = {"product-created-topic", "product-updated-topic", "product-deleted-topic"},
            groupId = "${spring.kafka.consumer.group-id:chat-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductEvent(@Payload String payload,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.OFFSET) long offset) throws Exception {
        try {
            String json = payload;
            if (json != null) {
                String t = json.trim();
                if (t.startsWith("\"") && t.endsWith("\"")) {
                    json = objectMapper.readValue(json, String.class);
                }
            }

            if ("product-created-topic".equals(topic)) {
                ProductCreatedEvent ev = objectMapper.readValue(json, ProductCreatedEvent.class);
                log.info("[RAG] CREATED offset={} id={} name={}", topic, offset, ev.id(), ev.name());
                ingest(ev);

            } else if ("product-updated-topic".equals(topic)) {
                ProductCreatedEvent ev = objectMapper.readValue(json, ProductCreatedEvent.class);
                log.info("[RAG] UPDATED offset={} id={} name={}", topic, offset, ev.id(), ev.name());
                rag.deleteByProductId(ev.id());
                log.info("[RAG] DELETED old vectors for productId={}", ev.id());
                // 2. Ingest thông tin mới
                ingest(ev);

            } else if ("product-deleted-topic".equals(topic)) {
                Map<String, Object> ev = objectMapper.readValue(json, new TypeReference<>() {});
                String productId = (String) ev.get("id");
                if (productId != null && !productId.isBlank()) {
                    log.info("[RAG] DELETED offset={} id={}", topic, offset, productId);
                    rag.deleteByProductId(productId);
                }
            }

        } catch (Exception e) {
            log.error("Kafka consume error topic={} offset={} payload={}", topic, offset, payload, e);
            throw e;
        }
    }

    private void ingest(ProductCreatedEvent ev) {
        if (ev == null) return;

        String id = nz(ev.id());
        String name = nz(ev.name());
        java.math.BigDecimal price = ev.price();
        Integer quantity = ev.quantity();
        String code = extractCode(name);

        // 1) Tổng quan
        String overview = ("Sản phẩm: %s%s. ID: %s. Giá: %s. Tổng tồn: %s.")
                .formatted(name, code.isEmpty() ? "" : " (mã " + code + ")",
                        id,
                        price == null ? "N/A" : price,
                        quantity == null ? "N/A" : quantity);
        rag.ingestText(overview, Map.of(
                "type", "product_info", "productId", id, "name", name, "code", code, "lang", "vi"
        ));

        // 2) Danh sách size (chuỗi)
        java.util.List<String> sizes = ev.sizes();
        if (sizes != null && !sizes.isEmpty()) {
            rag.ingestText("Các size: " + String.join(", ", sizes), Map.of(
                    "type", "size_guide", "productId", id, "code", code, "lang", "vi"
            ));
        }

        // 3) Tồn kho theo size (chi tiết)
        java.util.List<com.store.dto.SizeQty> sizesWithQty = ev.sizesWithQty();
        if (sizesWithQty != null && !sizesWithQty.isEmpty()) {
            String inv = sizesWithQty.stream()
                    .map(sq -> nz(sq.size()) + ": " + (sq.quantity() == null ? 0 : sq.quantity()))
                    .collect(java.util.stream.Collectors.joining(", "));
            rag.ingestText("Tồn kho theo size: " + inv, Map.of(
                    "type", "inventory", "productId", id, "code", code, "lang", "vi"
            ));
        }

        // 4) Ảnh
        if (ev.image() != null && !ev.image().isBlank()) {
            rag.ingestText("Ảnh sản phẩm: " + ev.image(), Map.of(
                    "type", "product_image", "productId", id, "code", code, "lang", "vi"
            ));
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("\\b[A-Z]{2,}\\d+\\b");
    private static String extractCode(String text) {
        if (text == null) return "";
        var m = CODE.matcher(text);
        return m.find() ? m.group() : "";
    }


}