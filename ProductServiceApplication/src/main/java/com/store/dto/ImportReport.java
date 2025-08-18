package com.store.dto;

import java.util.List;

public record ImportReport(int created, int updated, int failed, List<String> warnings) {
    public static ImportReport error(String msg) {
        return new ImportReport(0, 0, 1, List.of(msg));
    }
}