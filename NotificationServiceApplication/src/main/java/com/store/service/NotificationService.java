package com.store.service;
import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;

    public HttpHeaders createAuthHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();

        // Lấy token từ header Authorization hiện tại của request
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && !authHeader.isEmpty()) {
            headers.set("Authorization", authHeader);
        }

        return headers;
    }
    public ProductDTO getProductById(String productId) {
        String url = "http://product-service/api/products/" + productId;
        return restTemplate.getForObject(url, ProductDTO.class);
    }

    public void sendOrderEmail(OrderDTO orderDTO) {
        ProductDTO productDTO = getProductById(orderDTO.getProductId());

        double total = orderDTO.getQuantity() * productDTO.getPrice();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom("hi@demomailtrap.co");
            helper.setTo("namlun21072003@gmail.com");
            helper.setSubject("Đơn hàng mới #" + orderDTO.getOrderId());

            String body = "<p>Xin chào userId <b>" + orderDTO.getUserId() + "</b>,</p>" +
                    "<p>Đơn hàng #" + orderDTO.getOrderId() + " vừa được tạo thành công!</p>" +
                    "<p>Tổng tiền: <b>$" + total + "</b></p>";

            helper.setText(body, true);

            mailSender.send(message);
            System.out.println("Gửi email thành công!");
        } catch (MessagingException e) {
            System.err.println("Gửi email thất bại: " + e.getMessage());
        }
    }
}