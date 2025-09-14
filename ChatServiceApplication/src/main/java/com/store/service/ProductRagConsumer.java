package com.store.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRagConsumer {

    private final ObjectMapper objectMapper;
    private final RagService rag;

    // Nghe cả tạo mới & cập nhật
    @KafkaListener(
            topics = {"product-created-topic", "product-updated-topic"},
            groupId = "${spring.kafka.consumer.group-id:chat-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductEvent(@Payload String payload,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.OFFSET) long offset) throws Exception {
        try {
            String json = payload;
            // ✅ payload là một JSON string (double-encoded) -> unwrap
            if (json != null) {
                String t = json.trim();
                if (t.startsWith("\"") && t.endsWith("\"")) {
                    // bóc lớp chuỗi (ví dụ "\"{...}\"" -> "{...}")
                    json = objectMapper.readValue(json, String.class);
                }
            }

            ProductEvent ev = objectMapper.readValue(json, ProductEvent.class);
            log.info("[RAG] {} offset={} id={} name={} qty={} sizes={}",
                    topic, offset, ev.id(), ev.name(), ev.quantity(), ev.sizes());
            ingest(ev);

        } catch (Exception e) {
            log.error("Kafka consume error topic={} offset={} payload={}", topic, offset, payload, e);
            throw e; // để ErrorHandler đẩy sang DLT
        }
    }
    /** Ingest nội dung thân thiện truy hồi (có cả mã sản phẩm như JR8830 nếu bắt được) */
    private void ingest(ProductEvent ev) {
        if (ev == null) return;

        String id = nz(ev.id());
        String name = nz(ev.name());
        BigDecimal price = ev.price();
        Integer quantity = ev.quantity();
        String code = extractCode(name); // ví dụ "JR8830"

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
        List<String> sizes = ev.sizes();
        if (sizes != null && !sizes.isEmpty()) {
            rag.ingestText("Các size: " + String.join(", ", sizes), Map.of(
                    "type", "size_guide", "productId", id, "code", code, "lang", "vi"
            ));
        }

        // 3) Tồn kho theo size (chi tiết)
        List<SizeQty> sizesWithQty = ev.sizesWithQty();
        if (sizesWithQty != null && !sizesWithQty.isEmpty()) {
            String inv = sizesWithQty.stream()
                    .map(sq -> nz(sq.size()) + ": " + (sq.quantity() == null ? 0 : sq.quantity()))
                    .collect(Collectors.joining(", "));
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

    /** DTO null-safe cho event (bỏ qua field lạ để không vỡ parse) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductEvent(
            String id,
            String name,
            String image,
            BigDecimal price,
            Integer quantity,
            List<String> sizes,
            List<SizeQty> sizesWithQty
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SizeQty(String size, Integer quantity) {}

    private static String nz(String s) { return s == null ? "" : s; }

    /** Bắt mã sản phẩm dạng chữ in + số, ví dụ JR8830 */
    private static final Pattern CODE = Pattern.compile("\\b[A-Z]{2,}\\d+\\b");
    private static String extractCode(String text) {
        if (text == null) return "";
        var m = CODE.matcher(text);
        return m.find() ? m.group() : "";
    }
}
