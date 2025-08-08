package com.store.service;

import com.store.dto.OrderDTO;
import com.store.dto.ProductDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService emailService;

    @RetryableTopic(attempts = "5", dltTopicSuffix = "-dlt",
                    backoff = @Backoff (delay = 2_000, multiplier = 2))
    @KafkaListener(topics = "order-topic", groupId = "notification-group", containerFactory = "orderPlacedEventListenerFactory")
    public void handleOrderEvent(OrderDTO event) {
        System.out.println("Nhận được event từ Kafka: " + event);
        emailService.sendOrderEmail(event);

        // Nếu muốn test lại việc gửi email thất bại, hãy bỏ comment dòng dưới đây.
//        throw new RuntimeException("Loi khi gui email cho don hang: " + event.getOrderId());
    }

    @KafkaListener(topics = "order-topic-dlt", groupId = "notification-group", containerFactory = "orderPlacedEventListenerFactory")
    public void dltListener(OrderDTO event){
        System.out.println(" nhận được từ DLT. Cần xử lý thủ công cho đơn hàng ID: "+ event.getOrderId());
    }
}

