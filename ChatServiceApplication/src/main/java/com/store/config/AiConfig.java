package com.store.config;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class AiConfig {
    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port}")
    private int qdrantPort;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("""
                        Bạn là trợ lý trả lời ngắn gọn cho hệ thống bán hàng.
                        LUÔN trả lời 100% bằng TIẾNG VIỆT.
                        Không chèn câu xã giao tiếng Anh.
                        QUAN TRỌNG: KHÔNG dịch hoặc biến đổi tên sản phẩm/brand. 
                        Giữ nguyên y hệt chuỗi name từ cơ sở dữ liệu.
                        """)
                .build();
    }
    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder grpcClientBuilder = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false);
        return new QdrantClient(grpcClientBuilder.build());
    }
    @Bean
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(collectionName)
                .build();
    }
}