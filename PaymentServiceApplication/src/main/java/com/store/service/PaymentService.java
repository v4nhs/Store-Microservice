package com.store.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.model.OutboxEvent;
import com.store.model.Payment;
import com.store.model.PaymentStatus;
import com.store.repository.OutboxRepository;
import com.store.repository.PaymentRepository;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository payments;
    private final OutboxRepository outbox;
    private final ObjectMapper om = new ObjectMapper();

    /** Tạo/nhận payment PENDING theo orderId + idempotencyKey (an toàn retry). */
    @Transactional
    public Payment createOrGetPendingByOrder(String orderId,
                                             String userId,
                                             long amount,
                                             String currency,
                                             @Nullable String idempotencyKey) {
        String cur = (currency==null || currency.isBlank()) ? "VND" : currency;

        // 1) Ưu tiên theo idempotencyKey (nếu có)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var byKey = payments.findByIdempotencyKey(idempotencyKey);
            if (byKey.isPresent()) return sync(byKey.get(), userId, amount, cur);
        }

        // 2) Fallback theo orderId (một đơn chỉ có một payment): idempotent
        var byOrder = payments.findByOrderId(orderId);
        if (byOrder.isPresent()) return sync(byOrder.get(), userId, amount, cur);

        // 3) Tạo mới (có thể bị race condition -> bắt UniqueViolation để idempotent)
        Payment p = Payment.builder()
                .orderId(orderId).userId(userId).amount(amount).currency(cur)
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        try {
            return payments.save(p);
        } catch (DataIntegrityViolationException dup) {
            // Bị đụng UNIQUE (orderId hoặc idempotencyKey) do request song song -> lấy bản đã có
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                return payments.findByIdempotencyKey(idempotencyKey).orElseGet(
                        () -> payments.findByOrderId(orderId).orElseThrow()
                );
            }
            return payments.findByOrderId(orderId).orElseThrow();
        }
    }

    /** đồng bộ nhẹ dữ liệu lần đầu (không làm đổi semantics). */
    private Payment sync(Payment p, String userId, long amount, String currency) {
        boolean touched = false;
        if ((p.getUserId()==null || p.getUserId().isBlank()) && userId!=null) { p.setUserId(userId); touched=true; }
        if (p.getAmount()==null || p.getAmount()==0L) { p.setAmount(amount); touched=true; }
        if (p.getCurrency()==null || p.getCurrency().isBlank()) { p.setCurrency(currency); touched=true; }
        if (p.getStatus()==null) { p.setStatus(PaymentStatus.PENDING); touched=true; }
        if (touched) { p.setUpdatedAt(Instant.now()); p = payments.save(p); }
        return p;
    }

    /** Mark success -> ghi outbox (idempotent theo trạng thái). */
    @Transactional
    public void markSucceeded(String orderId, String gatewayTxnId) throws Exception {
        var p = payments.findByOrderId(orderId).orElseThrow();
        if (p.isTerminal()) return;
        p.setStatus(PaymentStatus.SUCCEEDED);
        p.setGatewayTxnId(gatewayTxnId);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        String payload = om.writeValueAsString(Map.of(
                "orderId", p.getOrderId(),
                "paymentId", "pay_" + p.getId(),
                "amount", p.getAmount(),
                "currency", p.getCurrency(),
                "gateway", "MOCK",
                "gatewayTxnId", p.getGatewayTxnId()
        ));
        outbox.save(OutboxEvent.builder()
                .eventType("PAYMENT_SUCCEEDED")
                .payload(payload)
                .status("NEW")
                .createdAt(Instant.now())
                .build());
    }

    /** Mark fail -> ghi outbox. */
    @Transactional
    public void markFailed(String orderId, String reason) throws Exception {
        var p = payments.findByOrderId(orderId).orElseThrow();
        if (p.isTerminal()) return;
        p.setStatus(PaymentStatus.FAILED);
        p.setUpdatedAt(Instant.now());
        payments.save(p);

        String payload = om.writeValueAsString(Map.of(
                "orderId", p.getOrderId(),
                "paymentId", "pay_" + p.getId(),
                "reason", reason==null?"UNKNOWN":reason
        ));
        outbox.save(OutboxEvent.builder()
                .eventType("PAYMENT_FAILED")
                .payload(payload)
                .status("NEW")
                .createdAt(Instant.now())
                .build());
    }
}