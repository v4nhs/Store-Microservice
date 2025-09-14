package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String KEY_CONFIRMED_PREFIX = "mail:sent:order:confirmed:";
    private static final String KEY_CANCELLED_PREFIX = "mail:sent:order:cancelled:";
    private static final Duration IDEMPOTENT_TTL = Duration.ofDays(7);

    /** Nếu payload bị wrap bằng dấu " ... " (chuỗi JSON lồng chuỗi), bỏ wrap để parse an toàn */
    private String unwrapIfQuoted(String payload) {
        try {
            if (payload != null && payload.length() > 1 && payload.charAt(0) == '"') {
                return objectMapper.readValue(payload, String.class);
            }
        } catch (Exception ignore) {}
        return payload;
    }

    /* ========================= ORDER CONFIRMED ========================= */

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2_000, multiplier = 2.0),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "order-confirmed",
            groupId = "notification-group" // dùng factory mặc định có StringDeserializer
    )
    public void handleOrderConfirmed(String payload) {
        try {
            String raw = unwrapIfQuoted(payload);
            OrderDTO event = objectMapper.readValue(raw, OrderDTO.class);
            System.out.println("Nhận event order-confirmed: " + event);

            String key = KEY_CONFIRMED_PREFIX + event.getOrderId();
            Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL);
            if (Boolean.FALSE.equals(firstTime)) return;

            try {
                emailService.sendOrderConfirmedEmail(event);
            } catch (Exception ex) {
                redis.delete(key);        // cho phép retry nếu gửi mail lỗi
                throw ex;
            }
        } catch (Exception e) {
            // ném lỗi để Kafka retry / đẩy DLT
            throw new RuntimeException("Parse/handle order-confirmed failed", e);
        }
    }

    /* ========================= ORDER CANCELLED ========================= */

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2_000, multiplier = 2.0),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
            topics = "order-cancelled",
            groupId = "notification-group"
    )
    public void handleOrderCancelled(String payload) {
        try {
            String raw = unwrapIfQuoted(payload);
            OrderDTO event = objectMapper.readValue(raw, OrderDTO.class);
            System.out.println("Nhận event order-cancelled: " + event);

            String key = KEY_CANCELLED_PREFIX + event.getOrderId();
            Boolean firstTime = redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENT_TTL);
            if (Boolean.FALSE.equals(firstTime)) return;

            try {
                emailService.sendOrderCancelledEmail(event);
            } catch (MailSendException ex) {
                redis.delete(key);        // cho phép retry lần sau
                System.err.println("MailSendException (CANCELLED): " + ex.getMessage());
            } catch (Exception ex) {
                redis.delete(key);
                throw ex;
            }
        } catch (Exception e) {
            throw new RuntimeException("Parse/handle order-cancelled failed", e);
        }
    }

    /* ========================= DLT HANDLERS ========================= */

    @KafkaListener(topics = "order-confirmed-dlt", groupId = "notification-group")
    public void confirmedDlt(String payload){
        try {
            OrderDTO event = objectMapper.readValue(unwrapIfQuoted(payload), OrderDTO.class);
            System.out.println("DLT order-confirmed. Cần xử lý tay cho order: " + event.getOrderId());
        } catch (Exception e) {
            System.out.println("DLT order-confirmed payload (raw): " + payload);
        }
    }

    @KafkaListener(topics = "order-cancelled-dlt", groupId = "notification-group")
    public void cancelledDlt(String payload){
        try {
            OrderDTO event = objectMapper.readValue(unwrapIfQuoted(payload), OrderDTO.class);
            System.out.println("DLT order-cancelled. Cần xử lý tay cho order: " + event.getOrderId());
        } catch (Exception e) {
            System.out.println("DLT order-cancelled payload (raw): " + payload);
        }
    }
}
