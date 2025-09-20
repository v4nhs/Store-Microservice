package com.store.keyword;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class KeywordExtractor {

    private static final Set<String> STOP = Set.of(
            "la","là","gia","giá","bao","nhiêu","bao nhiêu","có","co","không","khong","còn","con",
            "hàng","kho","xem","ảnh","ảnh","hình","hinh","image","photo","link","url",
            "sản","san","phẩm","pham","sản phẩm","san pham",
            "mua","bán","ban","order","đơn","don","đơn hàng","don hang",
            "size","màu","mau","mã","ma","ms",
            "giúp","giup","hỏi","hoi","cho","xin","thông tin","thong tin","về","ve","của","cua"
    );

    private static final Set<String> BRANDS = Set.of(
            "nike","adidas","puma","converse","vans","reebok","asics","new balance","nb","fila",
            "under armour","uniqlo","zara","h&m"
    );

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9\\s/+-]");
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");

    public record Keywords(String normalized, List<String> tokens, List<String> phrases) {}

    public static String normalize(String s) {
        if (s == null) return "";
        String nd = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", ""); // bỏ dấu
        nd = nd.toLowerCase(Locale.ROOT);
        nd = NON_ALNUM.matcher(nd).replaceAll(" ");
        nd = MULTISPACE.matcher(nd).replaceAll(" ").trim();
        return nd;
    }

    public static List<String> importantTokens(String normalized) {
        if (normalized.isBlank()) return List.of();
        String[] arr = normalized.split(" ");
        List<String> tokens = new ArrayList<>();
        for (String t : arr) {
            if (t.length() < 2) continue;
            if (STOP.contains(t)) continue;
            tokens.add(t);
        }
        // ghép brand 2 từ (vd: new balance)
        List<String> joined = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (i + 1 < tokens.size()) {
                String two = t + " " + tokens.get(i + 1);
                if (BRANDS.contains(two)) {
                    joined.add(two);
                    i++;
                    continue;
                }
            }
            joined.add(t);
        }
        return joined;
    }

    public static List<String> buildPhrases(List<String> tokens) {
        List<String> phrases = new ArrayList<>();
        int n = tokens.size();
        for (int i = 0; i < n; i++) {
            String w1 = tokens.get(i);
            phrases.add(w1);
            if (i + 1 < n) phrases.add(w1 + " " + tokens.get(i + 1));
            if (i + 2 < n) phrases.add(w1 + " " + tokens.get(i + 1) + " " + tokens.get(i + 2));
        }
        return phrases.stream()
                .map(String::trim)
                .filter(p -> p.length() >= 2)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.toList());
    }

    public static Keywords extract(String input) {
        String norm = normalize(input);
        List<String> tokens = importantTokens(norm);
        List<String> phrases = buildPhrases(tokens);
        return new Keywords(norm, tokens, phrases);
    }

    public static String bestPhrase(String q) {
        var k = extract(q);
        return k.phrases().isEmpty() ? k.normalized() : k.phrases().get(0);
    }
}
