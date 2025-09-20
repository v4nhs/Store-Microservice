package com.store.dto;

import lombok.Data;

import java.util.List;

@Data
public class PaypalErr {
    public String name;
    public String message;
    public String debug_id;
    public List<Detail> details;
    public List<Link> links;

    @Data public static class Detail { public String issue; public String description; }
    @Data public static class Link { public String href; public String rel; public String method; }

    public String firstIssue() {
        return (details != null && !details.isEmpty() && details.get(0) != null)
                ? details.get(0).issue : null;
    }
}