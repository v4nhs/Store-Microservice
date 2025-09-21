package com.store.service;

import com.store.dto.PaymentFailed;
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
    private static final String KEY_FAILED_PREFIX = "mail:sent:payment:failed:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    @KafkaListener(
            topics = "payment-succeeded",
            groupId = "notification-group"
    )
    public void onPaymentSucceeded(PaymentSucceeded evt) {
        String key = KEY_PAID_PREFIX + evt.getOrderId();
        if (Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL))) return;

        try {
            emailService.sendPaymentSucceededEmail(evt);
        } catch (Exception ex) {
            redis.delete(key); // rollback idempotency nếu lỗi
            throw ex;
        }
    }

    @KafkaListener(
            topics = "payment-failed",
            groupId = "notification-group"
    )
    public void onPaymentFailed(PaymentFailed evt) {
        String key = KEY_FAILED_PREFIX + evt.getOrderId();
        if (Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL))) return;

        try {
            emailService.sendPaymentFailedEmail(evt);
        } catch (Exception ex) {
            redis.delete(key); // rollback idempotency nếu lỗi
            throw ex;
        }
    }
}
