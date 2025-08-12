package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:test@example.com}")
    private String mailFrom;

    public ProductDTO getProductById(String productId) {
        try {
            String url = "http://product-service/api/products/" + productId;
            return restTemplate.getForObject(url, ProductDTO.class);
        } catch (Exception ex) {
            log.warn("Fallback getProductById thất bại: {}", ex.getMessage());
            return null;
        }
    }

    private String resolveTo(OrderDTO order) {
        return (order.getEmail() != null && !order.getEmail().isBlank())
                ? order.getEmail()
                : "test@inbox.mailtrap.io";
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

    public void sendOrderConfirmedEmail(OrderDTO order) {
        String name = order.getProductName();
        double price = order.getPrice();
        if ((name == null || name.isBlank()) || price <= 0) {
            ProductDTO p = getProductById(order.getProductId());
            if (p != null) {
                if (name == null || name.isBlank()) name = p.getName();
                if (price <= 0) price = p.getPrice();
            }
        }
        String safeName = (name == null || name.isBlank()) ? "(unknown)" : name;
        double total = price * order.getQuantity();

        String subject = "Đơn hàng #" + order.getOrderId() + " đã xác nhận";
        String body = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Đơn hàng <b>#%s</b> đã được <b>xác nhận</b>.</p>
            <p>Sản phẩm: <b>%s</b></p>
            <p>Giá: <b>$%.2f</b> x %d</p>
            <p>Tổng tiền: <b>$%.2f</b></p>
            <p>Trạng thái: <b>%s</b></p>
        """.formatted(
                order.getUserId(),
                order.getOrderId(),
                safeName,
                price,
                order.getQuantity(),
                total,
                (order.getStatus() == null ? "CONFIRMED" : order.getStatus())
        );

        safeSend(resolveTo(order), subject, body);
    }

    public void sendOrderCancelledEmail(OrderDTO order) {
        String name = order.getProductName();
        if (name == null || name.isBlank()) {
            ProductDTO p = getProductById(order.getProductId());
            if (p != null && p.getName() != null && !p.getName().isBlank()) {
                name = p.getName();
            }
        }
        String safeName = (name == null || name.isBlank()) ? "(unknown)" : name;

        String subject = "Đơn hàng #" + order.getOrderId() + " không thành công";
        String body = """
            <p>Xin chào <b>%s</b>,</p>
            <p>Rất tiếc, đơn hàng <b>#%s</b> của bạn <b>không thành công</b> (trạng thái: %s).</p>
            <p>Sản phẩm: <b>%s</b></p>
            <p>Lý do thường gặp: hết hàng hoặc số lượng không đủ.</p>
            <p>Bạn có thể thử giảm số lượng hoặc chọn sản phẩm khác.</p>
        """.formatted(
                order.getUserId(),
                order.getOrderId(),
                (order.getStatus() == null ? "CANCELLED" : order.getStatus()),
                safeName
        );

        safeSend(resolveTo(order), subject, body);
    }
}
