package com.store.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class IngestTextRequest {
    @NotBlank
    public String text;
    public Map<String, Object> meta;
}
