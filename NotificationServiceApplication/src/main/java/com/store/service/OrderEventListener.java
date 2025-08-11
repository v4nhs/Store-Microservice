package com.store.service;

import com.store.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.mail.MailSendException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService emailService;
    private final StringRedisTemplate redis;

    private static final String KEY_CONFIRMED_PREFIX = "mail:sent:order:confirmed:";
    private static final String KEY_CANCELLED_PREFIX = "mail:sent:order:cancelled:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2_000, multiplier = 2),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "order-confirmed",
            groupId = "notification-group",
            containerFactory = "orderPlacedEventListenerFactory"
    )
    public void handleOrderConfirmed(OrderDTO event) {
        System.out.println("Nhận event order-confirmed: " + event);

        String key = KEY_CONFIRMED_PREFIX + event.getOrderId();
        Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL);
        if (Boolean.FALSE.equals(firstTime)) return;

        try {
            emailService.sendOrderConfirmedEmail(event);
        } catch (Exception ex) {
            redis.delete(key);
            throw ex;
        }
    }
    @KafkaListener(
            topics = "order-cancelled",
            groupId = "notification-group",
            containerFactory = "orderPlacedEventListenerFactory"
    )
    public void handleOrderCancelled(OrderDTO event) {
        System.out.println("Nhận event order-cancelled: " + event);

        String key = KEY_CANCELLED_PREFIX + event.getOrderId();
        Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL);
        if (Boolean.FALSE.equals(firstTime)) return;

        try {
            emailService.sendOrderCancelledEmail(event);
        } catch (MailSendException ex) {
            redis.delete(key);
            System.err.println("MailSendException (CANCELLED): " + ex.getMessage());
        } catch (Exception ex) {
            redis.delete(key);
            throw ex;
        }
    }


    @KafkaListener(
            topics = "order-confirmed-dlt",
            groupId = "notification-group",
            containerFactory = "orderPlacedEventListenerFactory"
    )
    public void confirmedDlt(OrderDTO event){
        System.out.println("DLT order-confirmed. Cần xử lý tay cho order: " + event.getOrderId());
    }
    @KafkaListener(
            topics = "order-cancelled-dlt",
            groupId = "notification-group",
            containerFactory = "orderPlacedEventListenerFactory"
    )
    public void cancelledDlt(OrderDTO event){
        System.out.println("DLT order-cancelled. Cần xử lý tay cho order: " + event.getOrderId());
    }
}
