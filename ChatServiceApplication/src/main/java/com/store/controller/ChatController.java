package com.store.controller;

import com.store.dto.request.ChatRequest;
import com.store.service.ToolGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OpenAiChatModel chatModel;
    private final ToolGateway tools; // để dùng sau nếu muốn gọi qua các service khác

    @PostMapping
    public Map<String, Object> chat(@Validated @RequestBody ChatRequest req) {
        // Hướng dẫn ngắn cho bot (dùng chuỗi thường, không text block)
        String systemHint =
                "Bạn là trợ lý của cửa hàng. Nếu câu hỏi liên quan đến tình trạng đơn hàng, thanh toán,\n" +
                        "hãy đề nghị người dùng cung cấp orderId và sử dụng công cụ tương ứng.";

        String user = req.getMessage();
        String prompt = systemHint + "\nNgười dùng: " + user;

        String answer = chatModel.call(prompt);
        return Map.of("answer", answer);
    }
}
