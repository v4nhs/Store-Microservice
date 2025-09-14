package com.store.service;

import com.store.dto.PaymentSucceeded;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final NotificationService emailService;
    private final StringRedisTemplate redis;

    private static final String KEY_PAID_PREFIX = "mail:sent:payment:succeeded:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    @KafkaListener(
            topics = "payment-succeeded",
            groupId = "notification-group",
            containerFactory = "paymentSucceededListenerFactory"
    )
    public void onPaymentSucceeded(PaymentSucceeded evt) {
        String key = KEY_PAID_PREFIX + evt.getOrderId(); // ✅ dùng hằng thống nhất
        if (Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL))) return;

        try {
            emailService.sendPaymentSucceededEmail(evt);
        } catch (Exception ex) {
            redis.delete(key);
            throw ex;
        }
    }
}
