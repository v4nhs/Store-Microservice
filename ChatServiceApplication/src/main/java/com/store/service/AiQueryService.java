package com.store.service;

import com.store.dto.AiResult;
import com.store.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiQueryService {

    private final RagService ragService;
    private final ProductQueryService productQS;
    private final OrderQueryService orderQS;

    /* ===== Regex nhận dạng câu hỏi (giữ nguyên như bản cũ) ===== */
    private static final Pattern IMG_Q = Pattern.compile(
            "(?i)^(?:hãy|vui lòng|xin)?\\s*(?:xem|cho|hiển\\s*thị|hien\\s*thi)?\\s*(?:ảnh|hình|image|img|photo)(?:\\s+(?:của|cua))?\\s+(.+)$"
    );
    private static final Pattern PRICE_Q = Pattern.compile(
            "(?i)^(?:hãy|vui lòng|xin|cho|hỏi|hoi|lấy|lay)?\\s*(?:giá|gia|price)(?:\\s+(?:của|cua))?\\s+(.+)$"
    );
    private static final Pattern STOCK_Q = Pattern.compile(
            "(?i)^(?:hãy|vui lòng|xin|xem|check|kiểm\\s*tra|kiem\\s*tra|hỏi|hoi)?\\s*(?:tồn\\s*kho|ton\\s*kho|tồn|ton|stock|qty|quantity|số\\s*lượng|so\\s*luong)(?:\\s+(?:của|cua))?\\s+(.+)$"
    );
    private static final Pattern SIZE_Q = Pattern.compile(
            "(?i)^(?:hãy|vui lòng|xin|hỏi|hoi|xem)?\\s*(?:size|kích\\s*cỡ|kich\\s*co)(?:\\s+(?:của|cua))?\\s+(.+)$"
    );
    private static final Pattern ORDER_LIST_Q = Pattern.compile(
            "(?i).*(?:danh\\s*sách|danhsach|list|tất\\s*cả|tat\\s*ca)\\s*(?:đơn\\s*hàng|don\\s*hang|orders?).*"
    );
    private static final Pattern TAIL_PHRASE = Pattern.compile(
            "(?i)" +
                    "(?:\\b(là|la)\\s*bao\\s*nh[iì]eu.*$)" +
                    "|(?:\\bbao\\s*nh[iì]eu.*$)" +
                    "|(?:\\bco[n]?\\s*(hang|kho).*$)" +
                    "|(?:\\bc[oó]?\\s*size.*$)" +                 // ... có size ...
                    "|(?:\\b(ảnh|hình|image|img|photo).*$)"       // ... ảnh/hình ...
    );
    private static final Pattern ORDER_MINE_Q_NORM = Pattern.compile(
            "^(?:xem|liet\\s*ke|danh\\s*sach|list)?\\s*(?:don(?:\\s*hang)?)\\s*(?:cua)?\\s*(?:toi|minh)\\s*$"
    );
    // == Helper ==
    private static boolean containsAny(String text, String... phrases) {
        for (String p : phrases) if (text.contains(p)) return true;
        return false;
    }

    // == Từ-khóa đã normalize (không dấu) ==
    private static final String[] VERB_SHOW = {
            "cho xem", "xem", "hien thi", "liet ke", "danh sach", "xem danh sach",
            "list", "show", "view", "check", "history"
    };
    private static final String[] MINE = { "cua toi", "cua minh", "my", "toi", "minh" };
    private static final String[] ORDER_WORDS = { "don hang", "don", "orders", "order" };

    /* =======================
       1) API riêng cho PRODUCT
       Không yêu cầu đăng nhập
       ======================= */
    public AiResult handleProduct(String raw, int topK) {
        String q = raw == null ? "" : raw.trim();
        if (q.isBlank()) return AiResult.error(HttpStatus.BAD_REQUEST, "Thiếu câu hỏi.");

        // Ảnh
        Matcher mImg = IMG_Q.matcher(q);
        if (mImg.matches()) {
            String name = normalizeProductName(mImg.group(1));
            return productQS.find(name)
                    .map(AiQueryService::toImageResult)
                    .orElseGet(() -> AiResult.notFound("Không tìm thấy sản phẩm."));
        }

        // Giá
        Matcher mPrice = PRICE_Q.matcher(q);
        if (mPrice.matches()) {
            String name = normalizeProductName(mPrice.group(1));
            return productQS.find(name)
                    .map(AiQueryService::toPriceResult)
                    .orElseGet(() -> AiResult.notFound("Không tìm thấy sản phẩm."));
        }

        // Tồn kho
        Matcher mStock = STOCK_Q.matcher(q);
        if (mStock.matches()) {
            String name = normalizeProductName(mStock.group(1));
            return productQS.find(name)
                    .map(AiQueryService::toStockResult)
                    .orElseGet(() -> AiResult.notFound("Không tìm thấy sản phẩm."));
        }

        // Size
        Matcher mSize = SIZE_Q.matcher(q);
        if (mSize.matches()) {
            String name = normalizeProductName(mSize.group(1));
            return productQS.find(name)
                    .map(AiQueryService::toSizesResult)
                    .orElseGet(() -> AiResult.notFound("Không tìm thấy sản phẩm."));
        }

        // Fallback: RAG
        return AiResult.text(ragService.ask(q, topK));
    }

    /* ======================
       2) API riêng cho ORDER
       BẮT BUỘC đăng nhập
       - userId: id người dùng hiện tại
       - bearer: nguyên header "Bearer xxx" (forward qua client)
       ====================== */
    public AiResult handleOrder(String raw, int topK,
                                @Nullable String userId,
                                @Nullable String bearer) {
        String q = raw == null ? "" : raw.trim();
        if (q.isBlank()) return AiResult.error(HttpStatus.BAD_REQUEST, "Thiếu câu hỏi.");

        if (userId == null || userId.isBlank()) {
            return AiResult.error(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để hỏi về đơn hàng.");
        }
        String qNorm = vnNormalize(q);

        boolean asksMine =
                ORDER_MINE_Q_NORM.matcher(qNorm).matches()
                        || qNorm.equals("don hang cua toi")
                        || qNorm.equals("don cua toi")
                        || qNorm.equals("don hang cua minh")
                        || (containsAny(qNorm, VERB_SHOW) && containsAny(qNorm, ORDER_WORDS) && containsAny(qNorm, MINE))
                        || qNorm.contains("my orders") || qNorm.contains("order history")
                        || qNorm.contains("cac don hang cua toi")
                        || qNorm.contains("tat ca don hang cua toi")
                        || qNorm.contains("nhung don hang cua toi");

        if (asksMine) {
            List<Map<String, Object>> orders = orderQS.getAllForCurrentUser(bearer);
            if (orders == null || orders.isEmpty()) {
                return AiResult.notFound("Bạn chưa có đơn hàng nào.");
            }
            return toOrdersResult(orders);
        }

        if (ORDER_LIST_Q.matcher(q).matches()) {
            List<Map<String, Object>> orders = orderQS.getAllForCurrentUser(bearer);
            if (orders == null || orders.isEmpty()) {
                return AiResult.notFound("Bạn chưa có đơn hàng nào.");
            }
            return toOrdersResult(orders);
        }

        // Chi tiết đơn theo ID (giữ nguyên)
        String orderId = orderQS.extractOrderId(q);
        if (orderId != null) {
            Map<String, Object> order = orderQS.getByIdForCurrentUser(orderId, bearer);
            if (order == null || order.isEmpty()) {
                return AiResult.notFound("Không tìm thấy đơn hàng " + orderId + " của bạn.");
            }
            return toOrderResult(order);
        }

        // Hướng dẫn (giữ nguyên)
        return AiResult.error(HttpStatus.BAD_REQUEST,
                "Bạn hãy hỏi: \"đơn hàng của tôi\" hoặc \"chi tiết đơn <mã-đơn>\".");
    }

    /* ===== Chuẩn hoá tên sản phẩm  ===== */
    private static String normalizeProductName(String s) {
        String name = s == null ? "" : s.trim();
        name = name.replaceFirst("(?i)^(sản phẩm|san pham)\\s+", "");
        name = name.replaceAll("[\"'.,!?;:()\\[\\]]", " ");
        name = name.replaceAll("\\s+", " ").trim();
        name = name.replaceAll("[\"'.,!?;:()\\[\\]]", " ");
        name = name.replaceAll("\\s+", " ").trim();
        name = TAIL_PHRASE.matcher(name).replaceAll("");
        return name;
    }
    private static String vnNormalize(String s) {
        if (s == null) return "";
        String noMarks = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");
        return noMarks
                .toLowerCase(java.util.Locale.ROOT)
                .replace('đ','d')
                .replaceAll("[^0-9a-z\\- ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /* ===== Mapping trả về  ===== */

    private static AiResult toImageResult(ProductDTO p) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("id", p.getId());
        data.put("name", p.getName());
        data.put("image", p.getImage());
        data.put("price", p.getPrice());
        data.put("quantity", p.getQuantity());
        data.put("sizes", p.getSizes());

        return AiResult.builder()
                .httpStatus(HttpStatus.OK).status("ok").type("image")
                .plain(p.getImage()).redirectUrl(p.getImage())
                .data(data).build();
    }

    private static AiResult toPriceResult(ProductDTO p) {
        return AiResult.ok("price", Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "price", p.getPrice()
        ));
    }

    private static AiResult toStockResult(ProductDTO p) {
        return AiResult.ok("stock", Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "quantity", p.getQuantity(),
                "sizes", p.getSizes()
        ));
    }

    private static AiResult toSizesResult(ProductDTO p) {
        return AiResult.ok("sizes", Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "sizes", p.getSizes()
        ));
    }

    private static AiResult toOrderResult(Map<String, Object> order) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", order.get("id"));
        data.put("status", order.get("status"));
        data.put("totalAmount", order.get("totalAmount"));
        data.put("items", order.get("items"));
        return AiResult.ok("order", data);
    }

    private static AiResult toOrdersResult(List<Map<String, Object>> orders) {
        return AiResult.ok("orders", Map.of("orders", orders));
    }

}
