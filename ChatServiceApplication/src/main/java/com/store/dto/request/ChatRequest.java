package com.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String q;
    private Integer topK;
}
