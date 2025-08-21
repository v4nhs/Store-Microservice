package com.store.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.store.dto.OrderDTO;
import com.store.dto.PaymentSucceeded;
import com.store.dto.ProductDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:test@example.com}")
    private String mailFrom;

    @Value("${order.service.baseUrl:http://order-service}")
    private String orderBaseUrl;

    @Value("${product.service.baseUrl:http://product-service}")
    private String productBaseUrl;

    /* ===================== Helpers ===================== */

    public ProductDTO getProductById(String productId) {
        try {
            String url = productBaseUrl + "/api/products/" + productId;
            return restTemplate.getForObject(url, ProductDTO.class);
        } catch (Exception ex) {
            log.warn("Fallback getProductById thất bại: {}", ex.getMessage());
            return null;
        }
    }

    private OrderDetail getOrderDetail(String orderId) {
        try {
            String url = orderBaseUrl + "/api/orders/" + orderId;
            return restTemplate.getForObject(url, OrderDetail.class);
        } catch (Exception ex) {
            log.warn("Không lấy được order {}: {}", orderId, ex.getMessage());
            return null;
        }
    }

    private String resolveTo(OrderDTO order) {
        return (order.getEmail() != null && !order.getEmail().isBlank())
                ? order.getEmail()
                : "test@inbox.mailtrap.io";
    }

    private String resolveTo(PaymentSucceeded evt) {
        if (evt.getEmail() != null && !evt.getEmail().isBlank()) return evt.getEmail();
        OrderDetail od = getOrderDetail(evt.getOrderId());
        if (od != null && od.getEmail() != null && !od.getEmail().isBlank()) return od.getEmail();
        return "test@inbox.mailtrap.io";
    }

    private void safeSend(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Gửi email thành công tới: {}", to);
        } catch (MailSendException e) {
            log.error("MailSendException: {}", e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("MessagingException: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static String money(BigDecimal v) {
        if (v == null) return "-";
        return v.stripTrailingZeros().toPlainString();
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof BigDecimal bd) return bd;
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeProductName(String productId) {
        ProductDTO p = getProductById(productId);
        return (p != null && p.getName() != null && !p.getName().isBlank()) ? p.getName() : productId;
    }

    private static BigDecimal sumLineAmounts(List<OrderDetail.Item> items) {
        if (items == null || items.isEmpty()) return null;
        BigDecimal total = BigDecimal.ZERO;
        for (OrderDetail.Item it : items) {
            if (it.getLineAmount() != null) {
                total = total.add(it.getLineAmount());
            }
        }
        return total;
    }

    /* ===================== EMAIL TÌNH TRẠNG ĐƠN ===================== */

    /** XÁC NHẬN ĐƠN HÀNG (từ topic order-confirmed) */
    public void sendOrderConfirmedEmail(OrderDTO event) {
        // Ưu tiên lấy chi tiết đơn để render multi-items
        OrderDetail od = getOrderDetail(event.getOrderId());
        String to = resolveTo(event);
        String subject = "Đơn hàng #" + event.getOrderId() + " đã xác nhận";

        if (od != null && od.getItems() != null && !od.getItems().isEmpty()) {
            // Email dạng multi-items
            StringBuilder rows = new StringBuilder();
            for (OrderDetail.Item it : od.getItems()) {
                String name = safeProductName(it.getProductId());
                String unit = money(it.getUnitPrice());
                String line = money(it.getLineAmount());
                rows.append("""
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td style="text-align:right">%d</td>
                      <td style="text-align:right">%s</td>
                      <td style="text-align:right">%s</td>
                    </tr>
                    """.formatted(it.getProductId(), name,
                        Objects.requireNonNullElse(it.getQuantity(), 0),
                        unit, line));
            }

            BigDecimal displayTotal = sumLineAmounts(od.getItems());

            String body = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Đơn hàng <b>#%s</b> đã được <b>xác nhận</b>.</p>
                <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%%">
                  <thead>
                    <tr>
                      <th>Product ID</th>
                      <th>Tên sản phẩm</th>
                      <th style="text-align:right">SL</th>
                      <th style="text-align:right">Đơn giá</th>
                      <th style="text-align:right">Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    %s
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colspan="4" style="text-align:right"><b>Tổng tiền</b></td>
                      <td style="text-align:right"><b>%s</b></td>
                    </tr>
                  </tfoot>
                </table>
                <p>Trạng thái: <b>%s</b></p>
                <p><b>Vui lòng tiến hành thanh toán</b></p>
                """.formatted(
                    (od.getUserId() == null ? "bạn" : od.getUserId()),
                    od.getId(),
                    rows.toString(),
                    money(displayTotal != null ? displayTotal : od.getTotalAmount()),
                    (od.getStatus() == null ? "CONFIRMED" : od.getStatus())
            );
            safeSend(to, subject, body);
            return;
        }

        String name = event.getProductName();
        BigDecimal price = event.getPrice();
        if ((name == null || name.isBlank()) || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            ProductDTO p = getProductById(event.getProductId());
            if (p != null) {
                if (name == null || name.isBlank()) name = p.getName();
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) price = p.getPrice();
            }
        }
        if (price == null) price = BigDecimal.ZERO;
        String safeName = (name == null || name.isBlank()) ? "(unknown)" : name;
        BigDecimal total = price.multiply(BigDecimal.valueOf(event.getQuantity())); // không ép scale

        String body = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Đơn hàng <b>#%s</b> đã được <b>xác nhận</b>.</p>
            <p>Sản phẩm: <b>%s</b></p>
            <p>Giá: <b>%s</b> x %d</p>
            <p>Tổng tiền: <b>%s</b></p>
            <p>Trạng thái: <b>%s</b></p>
            <p><b>Vui lòng thanh toán</b></p>
            """.formatted(
                event.getUserId(),
                event.getOrderId(),
                safeName,
                money(price),
                event.getQuantity(),
                money(total),
                (event.getStatus() == null ? "CONFIRMED" : event.getStatus())
        );
        safeSend(to, subject, body);
    }

    /** HUỶ/THẤT BẠI ĐƠN HÀNG (từ topic order-cancelled) */
    public void sendOrderCancelledEmail(OrderDTO event) {
        OrderDetail od = getOrderDetail(event.getOrderId());
        String to = resolveTo(event);
        String subject = "Đơn hàng #" + event.getOrderId() + " không thành công";

        if (od != null && od.getItems() != null && !od.getItems().isEmpty()) {
            StringBuilder rows = new StringBuilder();
            for (OrderDetail.Item it : od.getItems()) {
                String name = safeProductName(it.getProductId());
                String unit = money(it.getUnitPrice());
                String line = money(it.getLineAmount());
                rows.append("""
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td style="text-align:right">%d</td>
                      <td style="text-align:right">%s</td>
                      <td style="text-align:right">%s</td>
                    </tr>
                    """.formatted(it.getProductId(), name,
                        Objects.requireNonNullElse(it.getQuantity(), 0),
                        unit, line));
            }

            BigDecimal displayTotal = sumLineAmounts(od.getItems());

            String body = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Rất tiếc, đơn hàng <b>#%s</b> của bạn <b>không thành công</b>.</p>
                <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%%">
                  <thead>
                    <tr>
                      <th>Product ID</th>
                      <th>Tên sản phẩm</th>
                      <th style="text-align:right">SL</th>
                      <th style="text-align:right">Đơn giá</th>
                      <th style="text-align:right">Thành tiền</th>
                    </tr>
                  </thead>
                  <tbody>
                    %s
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colspan="4" style="text-align:right"><b>Tổng tiền</b></td>
                      <td style="text-align:right"><b>%s</b></td>
                    </tr>
                  </tfoot>
                </table>
                <p>Trạng thái: <b>%s</b></p>
                <p>Bạn có thể thử giảm số lượng hoặc chọn sản phẩm khác.</p>
                """.formatted(
                    (od.getUserId() == null ? "bạn" : od.getUserId()),
                    od.getId(),
                    rows.toString(),
                    money(displayTotal != null ? displayTotal : od.getTotalAmount()),
                    (od.getStatus() == null ? "CANCELLED" : od.getStatus())
            );
            safeSend(to, subject, body);
            return;
        }

        String name = event.getProductName();
        if (name == null || name.isBlank()) {
            ProductDTO p = getProductById(event.getProductId());
            if (p != null && p.getName() != null && !p.getName().isBlank()) name = p.getName();
        }
        String safeName = (name == null || name.isBlank()) ? "(unknown)" : name;
        String body = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Rất tiếc, đơn hàng <b>#%s</b> của bạn <b>không thành công</b> (trạng thái: %s).</p>
            <p>Sản phẩm: <b>%s</b></p>
            <p>Lý do thường gặp: hết hàng hoặc số lượng không đủ.</p>
            <p>Bạn có thể thử giảm số lượng hoặc chọn sản phẩm khác.</p>
            """.formatted(
                event.getUserId(),
                event.getOrderId(),
                (event.getStatus() == null ? "CANCELLED" : event.getStatus()),
                safeName
        );
        safeSend(to, subject, body);
    }

    /* ===================== EMAIL THANH TOÁN ===================== */

    public void sendPaymentSucceededEmail(PaymentSucceeded evt) {
        String to = resolveTo(evt);
        String displayName = (evt.getCustomerName() != null && !evt.getCustomerName().isBlank())
                ? evt.getCustomerName() : "bạn";

        String subject = "[Store] Đơn #" + evt.getOrderId() + " đã thanh toán";
        BigDecimal amount = toBigDecimal(evt.getAmount());
        String body = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Đơn hàng <b>#%s</b> đã được <b>thanh toán thành công</b>.</p>
            <ul>
                <li>Phương thức: <b>%s</b></li>
                <li>Nhà cung cấp: <b>%s</b></li>
                <li>Mã giao dịch: <b>%s</b></li>
                <li>Số tiền: <b>%s</b></li>
                <li>Trạng thái: <b>%s</b></li>
            </ul>
            <p>Cảm ơn bạn đã mua sắm tại Store!</p>
            """.formatted(
                displayName,
                evt.getOrderId(),
                nullToDash(evt.getMethod()),
                nullToDash(evt.getProvider()),
                nullToDash(evt.getTransactionRef()),
                money(amount),
                nullToDash(evt.getStatus())
        );
        safeSend(to, subject, body);
    }

    private String nullToDash(Object v) { return v == null ? "-" : String.valueOf(v); }

    /* ===================== Lightweight DTOs cho Order-detail ===================== */

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderDetail {
        private String id;
        private String userId;
        private String status;
        private BigDecimal totalAmount;
        private String email;
        private List<Item> items;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Item {
            private String id;
            private String productId;
            private Integer quantity;
            private BigDecimal unitPrice;
            private BigDecimal lineAmount;
            private String itemStatus;
        }
    }
}
