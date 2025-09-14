package com.store.service;


import com.store.client.ChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiGatewayService {
    private final ChatClient chatClient;
    public String ask(String q, String authHeader) {
        if (!StringUtils.hasText(q)) return "Bạn vui lòng nhập câu hỏi.";
        return chatClient.ask(q, authHeader);
    }
}