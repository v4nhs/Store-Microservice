package com.store.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    public String q;
    public String message;
    public String format;
    @Min(1)
    @Max(20)
    public Integer topK;
}
