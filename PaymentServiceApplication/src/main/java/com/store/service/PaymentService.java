package com.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.store.dto.OrderDTO;
import com.store.dto.PaymentFailed;
import com.store.dto.PaymentRequest;
import com.store.dto.PaymentSucceeded;
import com.store.model.OutboxEvent;
import com.store.model.Payment;
import com.store.model.PaymentMethod;
import com.store.model.PaymentStatus;
import com.store.repository.OutboxRepository;
import com.store.repository.PaymentRepository;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepo;
    private final OutboxRepository outboxRepo;
    private final StringRedisTemplate redis;
    private final RestTemplate restTemplate;
    private final ObjectMapper om;

    @Value("${order.service.base-url}")
    private String orderBaseUrl;

    private String orderUrl(String orderId) {
        return orderBaseUrl + "/api/orders/" + orderId;
    }

    @Value("${service.user.base-url:http://localhost:8081}")
    private String userBaseUrl;

    private String userUrl(String userId) {
        return userBaseUrl + "/api/users/" + userId; // chỉnh path nếu API bạn khác
    }

    private static final String LUA_IDEM_SET_IF_ABSENT =
            "if redis.call('SETNX', KEYS[1], '1') == 1 then " +
                    "  redis.call('EXPIRE', KEYS[1], ARGV[1]); " +
                    "  return 1; " +
                    "else return 0; end";

    private static final Duration IDEM_TTL = Duration.ofMinutes(10);

    @Transactional
    public Payment pay(PaymentRequest req, @Nullable String authorizationHeader) {
        String orderId = req.getOrderId();
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (req.getMethod() == null) {
            req.setMethod(PaymentMethod.COD);
        }

        log.info("[PAY][IN ] orderId={} method={} idemKey={} provider={} amount={} returnUrl={}",
                orderId, req.getMethod(), req.getIdempotencyKey(), req.getProvider(), req.getAmount(), req.getReturnUrl());

        // 1) Lấy amount từ order-service nếu thiếu/không hợp lệ
        BigDecimal amount = req.getAmount();
        String email = null;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            String url = orderUrl(orderId);
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                if (authorizationHeader != null && !authorizationHeader.isBlank()) {
                    headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
                }
                ResponseEntity<OrderDTO> resp = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<Void>(headers), OrderDTO.class);

                OrderDTO order = resp.getBody();
                if (order == null || order.getTotalAmount() == null
                        || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException("Order totalAmount not available for orderId=" + orderId);
                }
                amount = order.getTotalAmount();
                email = order.getEmail();
                log.info("[PAY][ORD] fetched from OrderService: amount={} email={}", amount, email);
            } catch (ResourceAccessException e) {
                log.error("[PAY][ERR] Cannot reach OrderService at {}: {}", orderBaseUrl, e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Order service unreachable at " + orderBaseUrl, e);
            }
        }

        // 2) Idempotency bằng Redis + Lua — theo cặp (orderId, idempotencyKey)
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            String key = "idem:payment:" + orderId + ":" + req.getIdempotencyKey();
            log.info("[IDEMP] try SETNX key={} ttlSec={}", key, IDEM_TTL.toSeconds());
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_IDEM_SET_IF_ABSENT, Long.class);
            Long ok = redis.execute(script, List.of(key), String.valueOf(IDEM_TTL.toSeconds()));
            log.info("[IDEMP] result ok={} (1=new, 0=duplicate)", ok);

            if (ok == null || ok == 0L) {
                // Duplicate trong TTL → lấy record cũ rồi hòa giải nếu còn PENDING
                Payment dup = paymentRepo.findByOrderId(orderId)
                        .orElseGet(() -> paymentRepo.findByIdempotencyKey(req.getIdempotencyKey())
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.CONFLICT, "Duplicate payment request for orderId=" + orderId)));

                log.info("[IDEMP] duplicate-hit -> existing id={} status={} method={} provider={} amount={} idemKey={}",
                        dup.getId(), dup.getStatus(), dup.getMethod(), dup.getProvider(), dup.getAmount(), dup.getIdempotencyKey());

                if (dup.getStatus() == PaymentStatus.PENDING) {
                    PaymentMethod pm0 = dup.getMethod();
                    String pv0 = dup.getProvider();
                    BigDecimal am0 = dup.getAmount();
                    String ik0 = dup.getIdempotencyKey();

                    boolean changed = false;
                    if (req.getMethod() != null && dup.getMethod() != req.getMethod()) { dup.setMethod(req.getMethod()); changed = true; }
                    if (req.getProvider() != null && (dup.getProvider() == null || !dup.getProvider().equals(req.getProvider()))) { dup.setProvider(req.getProvider()); changed = true; }
                    if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0
                            && dup.getAmount().compareTo(req.getAmount()) != 0) { dup.setAmount(req.getAmount()); changed = true; }

                    if (changed) {
                        dup = paymentRepo.save(dup);
                        log.info("[RECON] duplicate reconciled id={} method {}->{} | provider {}->{} | amount {}->{} | idemKey {}",
                                dup.getId(),
                                pm0, dup.getMethod(),
                                pv0, dup.getProvider(),
                                am0, dup.getAmount(),
                                ik0);
                    } else {
                        log.info("[RECON] duplicate no-change id={}", dup.getId());
                    }
                } else {
                    log.info("[IDEMP] existing not PENDING -> return as-is id={} status={}", dup.getId(), dup.getStatus());
                }
                log.info("[RETURN] id={} orderId={} status={} method={} provider={} amount={} idemKey={}",
                        dup.getId(), dup.getOrderId(), dup.getStatus(), dup.getMethod(), dup.getProvider(), dup.getAmount(), dup.getIdempotencyKey());
                return dup;
            }
        }

        // 3) Lấy hoặc tạo Payment theo orderId (chống race)
        Optional<Payment> existing = paymentRepo.findByOrderId(orderId);
        BigDecimal finalAmount = amount;

        Payment p = existing.orElseGet(() -> {
            Payment np = new Payment();
            np.setOrderId(orderId);
            np.setAmount(finalAmount);
            np.setStatus(PaymentStatus.PENDING);
            np.setMethod(req.getMethod());
            np.setProvider(req.getProvider());
            np.setIdempotencyKey(req.getIdempotencyKey());
            try {
                Payment saved = paymentRepo.save(np);
                log.info("[PAY][NEW] created id={} status={} method={} provider={} amount={} idemKey={}",
                        saved.getId(), saved.getStatus(), saved.getMethod(), saved.getProvider(), saved.getAmount(), saved.getIdempotencyKey());
                return saved;
            } catch (DataIntegrityViolationException e) {
                log.warn("[PAY][RACE] create collided, fetching existing for orderId={}", orderId);
                return paymentRepo.findByOrderId(orderId)
                        .orElseGet(() -> paymentRepo.findByIdempotencyKey(req.getIdempotencyKey())
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.CONFLICT, "Duplicate payment request for orderId=" + orderId)));
            }
        });

        if (existing.isPresent()) {
            log.info("[PAY][USE] existing id={} status={} method={} provider={} amount={} idemKey={}",
                    p.getId(), p.getStatus(), p.getMethod(), p.getProvider(), p.getAmount(), p.getIdempotencyKey());
        }

        // 4) Cùng order nhưng đổi idempotencyKey / method / provider / amount → hòa giải khi còn PENDING
        if (p.getStatus() == PaymentStatus.PENDING) {
            PaymentMethod pm0 = p.getMethod();
            String pv0 = p.getProvider();
            BigDecimal am0 = p.getAmount();
            String ik0 = p.getIdempotencyKey();

            boolean changed = false;
            if (req.getIdempotencyKey() != null && (p.getIdempotencyKey() == null || !p.getIdempotencyKey().equals(req.getIdempotencyKey()))) {
                p.setIdempotencyKey(req.getIdempotencyKey()); changed = true;
            }
            if (req.getMethod() != null && p.getMethod() != req.getMethod()) {
                p.setMethod(req.getMethod()); changed = true;
            }
            if (req.getProvider() != null && (p.getProvider() == null || !p.getProvider().equals(req.getProvider()))) {
                p.setProvider(req.getProvider()); changed = true;
            }
            if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0
                    && p.getAmount().compareTo(req.getAmount()) != 0) {
                p.setAmount(req.getAmount()); changed = true;
            }

            if (changed) {
                p = paymentRepo.save(p);
                log.info("[RECON] normal reconciled id={} method {}->{} | provider {}->{} | amount {}->{} | idemKey {}->{}",
                        p.getId(), pm0, p.getMethod(), pv0, p.getProvider(), am0, p.getAmount(), ik0, p.getIdempotencyKey());
            } else {
                log.info("[RECON] normal no-change id={}", p.getId());
            }
        }

        // Nếu đã xử lý rồi → trả luôn
        if (p.getStatus() != PaymentStatus.PENDING) {
            log.info("[RETURN] id={} orderId={} status={} method={} provider={} amount={} idemKey={}",
                    p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getProvider(), p.getAmount(), p.getIdempotencyKey());
            return p;
        }

        // 5) Xử lý thanh toán (demo)
        if (req.getMethod() == PaymentMethod.COD) {
            log.info("[RETURN] PENDING(COD) id={} orderId={} status={} method={} amount={} idemKey={}",
                    p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getAmount(), p.getIdempotencyKey());
            return p; // COD: giữ PENDING
        }

        boolean success = true; // demo online OK
        p.setAmount(amount);
        p.setStatus(success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepo.save(p);
        log.info("[PROC ] online processed id={} status={} method={} amount={}", p.getId(), p.getStatus(), p.getMethod(), p.getAmount());

        // 6) Outbox
        try {
            if (success) {
                var evt = PaymentSucceeded.builder()
                        .orderId(orderId)
                        .paymentId(p.getId())
                        .amount(p.getAmount())
                        .status(p.getStatus().name())
                        .email(email)
                        .method(p.getMethod().name())
                        .provider(p.getProvider())
                        .build();
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateId(p.getId())
                        .aggregateType("PAYMENT")
                        .eventType("PAYMENT_SUCCESS")
                        .payload(om.writeValueAsString(evt))
                        .status("NEW")
                        .build());
                log.info("[OUTBX] queued PAYMENT_SUCCESS for id={}", p.getId());
            } else {
                var evt = PaymentFailed.builder()
                        .orderId(orderId)
                        .paymentId(p.getId())
                        .reason("GATEWAY_ERROR")
                        .build();
                outboxRepo.save(OutboxEvent.builder()
                        .aggregateId(p.getId())
                        .aggregateType("PAYMENT")
                        .eventType("PAYMENT_FAILED")
                        .payload(om.writeValueAsString(evt))
                        .status("NEW")
                        .build());
                log.info("[OUTBX] queued PAYMENT_FAILED for id={}", p.getId());
            }
        } catch (JsonProcessingException e) {
            log.error("[OUTBX][ERR] Serialize outbox payload failed: {}", e.getOriginalMessage());
            p.setStatus(PaymentStatus.FAILED);
            paymentRepo.save(p);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot serialize outbox event", e);
        }

        log.info("[RETURN] id={} orderId={} status={} method={} provider={} amount={} idemKey={}",
                p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getProvider(), p.getAmount(), p.getIdempotencyKey());
        return p;
    }



    /**
     * ⚠️ KHÔNG set cứng COD ở overload này nữa.
     * Tốt nhất: xoá hẳn overload để không ai gọi nhầm.
     */
    @Deprecated
    @Transactional
    public Payment pay(String orderId,
                       @Nullable BigDecimal amount,
                       @Nullable String idempotencyKey,
                       @Nullable String authorizationHeader) {
        PaymentRequest req = PaymentRequest.builder()
                .orderId(orderId)
                .amount(amount)
                .idempotencyKey(idempotencyKey)
                // .method(PaymentMethod.COD) // bỏ set cứng
                .build();
        return pay(req, authorizationHeader);
    }

    public Optional<Payment> findByOrderId(String orderId) {
        return paymentRepo.findByOrderId(orderId);
    }
}
