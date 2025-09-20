package com.store.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Match;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore; // Thêm import này
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Log4j2
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final QdrantClient qdrantClient;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    public String ask(String userQuestion, int topK) {
        var hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(userQuestion).topK(topK).build()
        );

        String context = hits.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));
        if (context.isBlank()) {
            return "Xin lỗi, tôi chưa tìm thấy thông tin phù hợp trong dữ liệu. Vui lòng hỏi cách khác hoặc liên hệ CSKH.";
        }

        String system = """
            Bạn là trợ lý AI cho khách hàng trả lời về sản phẩm, đơn hàng, dịch vụ và chính sách của cửa hàng. Chỉ trả lời bằng tiếng Việt.
            Không dùng tiếng Anh. Không dịch/đổi tên thương hiệu hoặc tên sản phẩm.
            Nếu hỏi về thông tin sản phẩm thì không cần authencated, còn hỏi về đơn hàng thì chưa đăng nhập sẽ yêu cầu đăng nhập.
            Tất cả đều kết thúc bằng câu cảm ơn lịch sự.
            Trả lời ngắn gọn, đúng trọng tâm và dựa trên CONTEXT.
            Nếu không chắc, hãy nói rõ ràng.
            """;

        String prompt = """
            CONTEXT:
            %s

            CÂU HỎI: %s
            Hướng dẫn: Trả lời HOÀN TOÀN bằng tiếng Việt 
            và trong ngữ cảnh đang giao tiếp với khách hàng 
            câu mở đầu luôn là Xin chào anh/chị và kết thúc là cửa hàng cảm ơn anh/chị đã quan tâm đến sản phẩm nếu hỏi về sản phẩm.
            """.formatted(context, userQuestion);

        return chatClient.prompt()
                .system(system)
                .user(prompt)
                .call()
                .content();
    }

    public void ingestText(String text, Map<String, Object> meta) {
        vectorStore.add(List.of(new Document(text, meta == null ? Map.of() : meta)));
    }

    public void deleteByProductId(String productId) {
        if (productId == null || productId.isBlank()) return;
        try {
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("productId")
                                    .setMatch(Match.newBuilder().setText(productId).build())
                                    .build())
                            .build())
                    .build();

            qdrantClient.deleteAsync(collectionName, filter).get();
            log.info("Đã xóa vector theo productId: {}", productId);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Lỗi xóa vector productId: {}", productId, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lỗi khi xóa dữ liệu trên Qdrant", e);
        }
    }
}