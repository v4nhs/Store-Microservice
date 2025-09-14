package com.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public String ask(String userQuestion, int topK) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(userQuestion).topK(topK).build()
        );

        String context = hits.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        if (context.isBlank()) {
            return "Không tìm thấy trong dữ liệu. Vui lòng hỏi cách khác hoặc liên hệ CSKH.";
        }

        String system = """
            Bạn là trợ lý AI cho khách hàng. Trả lời ngắn gọn, dựa trên CONTEXT.
            Nếu không chắc, hãy nói rõ ràng.
            """;

        String prompt = """
            CONTEXT:
            %s

            CÂU HỎI: %s
            Trả lời bằng tiếng Việt.
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
}
