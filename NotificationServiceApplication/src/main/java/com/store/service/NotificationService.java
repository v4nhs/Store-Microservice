package com.store.service;
import com.store.dto.OrderEvent;
import com.store.model.Notification;
import com.store.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, OrderEvent> redisTemplate;
    private final JavaMailSender mailSender;

    @KafkaListener(topics = "order-topic", groupId = "notification-group")
    public void handleOrderEvent(OrderEvent event) {
        // 1. Lưu DB
        Notification noti = new Notification();
        noti.setOrderId(event.getOrderId());
        noti.setUserEmail(event.getUserEmail());
        noti.setMessage(event.getMessage());
        notificationRepository.save(noti);

        // 2. Cache Redis
        redisTemplate.opsForValue().set("order:" + event.getOrderId(), event);

        // 3. Gửi Email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(event.getUserEmail());
        message.setSubject("Thông báo đơn hàng");
        message.setText(event.getMessage());
        mailSender.send(message);
    }
}