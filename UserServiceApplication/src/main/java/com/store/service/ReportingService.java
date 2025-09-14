package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final OrderProductHttpClient http;

    public String answerOrderStatus(String orderId) {
        OrderDTO o = http.getOrderById(orderId);
        if (o == null) return "Không có dữ liệu.";
        return "Đơn " + o.getId() + " hiện ở trạng thái " + o.getStatus() + ".";
    }

    public String answerOrderTotal(String orderId) {
        OrderDTO o = http.getOrderById(orderId);
        if (o == null || o.getTotalAmount() == null) return "Không có dữ liệu.";
        return "Tổng tiền đơn " + o.getId() + " là " + o.getTotalAmount().toPlainString() + ".";
    }

    public String answerProductStockById(String productId) {
        ProductDTO p = http.getProductById(productId);
        if (p == null) return "Không có dữ liệu.";
        return "Sản phẩm " + p.getName() + " (ID " + p.getId() + ") còn " + p.getQuantity() + " đơn vị.";
    }

    // Chỉ bật khi đã có endpoint /api/orders/count
    public String answerUserOrderCountByStatus(String userId, String status) {
        Long count = http.countOrdersByUserAndStatus(userId, status);
        if (count == null) return "Không có dữ liệu.";
        return "User " + userId + " có " + count + " đơn ở trạng thái " + status + ".";
    }
    // 1) Thêm các regex & hàm sanitize + findUuid ưu tiên "#<uuid>"
    private static final Pattern HASHED_UUID =
            Pattern.compile("(?i)#\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern UUID_RE =
            Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern STOCK_INTENT =
            Pattern.compile("(?iu).*(stock|tồn|ton|tồn kho|quantity|số lượng|so luong|kho|inventory|qty|còn).*");

    private static String sanitize(String q) {
        if (q == null) return "";
        String s = q;
        // loại control chars (tránh trường hợp %01, v.v.)
        s = s.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        // bỏ các ngoặc gây nhiễu
        s = s.replaceAll("[\\{\\}\\[\\]]", " ");
        // gom khoảng trắng
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String findUuid(String s) {
        var m = HASHED_UUID.matcher(s);
        if (m.find()) return m.group(1);
        m = UUID_RE.matcher(s);
        return m.find() ? m.group() : null;
    }

    // 2) Trong tryAnswer(...) gọi sanitize trước rồi tìm UUID
    public String tryAnswer(String q) {
        if (q == null) return null;
        String s = sanitize(q).toLowerCase();
        String id = findUuid(s);

        // 1) Trạng thái đơn
        if (s.matches(".*(trang thai|trạng thái|status).*") && id != null) {
            return answerOrderStatus(id); // sẽ trả “Không có dữ liệu.” nếu không thấy
        }

        // 2) Tổng đơn  ← bật lại nhánh tổng
        if (s.matches(".*(tong|tổng|total).*") && id != null) {
            return answerOrderTotal(id); // sẽ trả “Không có dữ liệu.” nếu null/không có total
        }

        // 3) “đơn/order/id …” → mặc định trả trạng thái
        if (id != null && s.matches(".*(don|đơn|order|ma don|mã đơn|id).*")) {
            return answerOrderStatus(id);
        }

        // 4) Stock theo productId
        if (STOCK_INTENT.matcher(s).matches()) {
            if (id != null) return answerProductStockById(id); // nếu không thấy → “Không có dữ liệu.”
            var m = Pattern.compile("id\\s*[:#]?\\s*([0-9a-f-]{36})", Pattern.CASE_INSENSITIVE).matcher(s);
            if (m.find()) return answerProductStockById(m.group(1));
            return "Bạn vui lòng cung cấp ID sản phẩm dạng #<UUID> (ví dụ: #3a56473b-d677-469b-9371-219dbfe9834d).";
        }

        // 5) Đếm đơn theo status của user (nếu có)
        if (s.matches(".*user\\s+[a-z0-9-]+.*(bao nhieu|bao nhiêu|how many).*(pending|confirmed|cancelled|shipped|delivered).*")) {
            String userId = s.replaceAll(".*user\\s+([a-z0-9-]+).*", "$1");
            String status = s.replaceAll(".*(pending|confirmed|cancelled|shipped|delivered).*", "$1").toUpperCase();
            return answerUserOrderCountByStatus(userId, status); // nếu không thấy → “Không có dữ liệu.”
        }
        return null;
    }
}
