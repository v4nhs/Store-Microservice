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
import com.store.model.PaymentResult;
import com.store.paypal.PayPalClient;
import com.store.processor.PaymentProcessor;
import com.store.repository.OutboxRepository;
import com.store.repository.PaymentRepository;
import io.micrometer.common.lang.Nullable;
import jakarta.annotation.PostConstruct;
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
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final OutboxRepository outboxRepo;
    private final StringRedisTemplate redis;
    private final RestTemplate restTemplate;
    private final ObjectMapper om;
    private final PayPalClient payPalClient;

    private final List<PaymentProcessor> processorBeans;
    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    @PostConstruct
    void initProcessors() {
        processors.clear();
        for (PaymentProcessor p : processorBeans) {
            try {
                processors.put(p.method(), p);
                log.info("[PROC] registered processor for method={}", p.method());
            } catch (Exception ex) {
                log.warn("[PROC] skip {} due to {}", p.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }

    @Value("${order.service.base-url}")
    private String orderBaseUrl;

    private String orderUrl(String orderId) {
        return orderBaseUrl + "/api/orders/" + orderId;
    }

    @Value("${service.user.base-url:http://localhost:8081}")
    private String userBaseUrl;

    private String userUrl(String userId) {
        return userBaseUrl + "/api/users/" + userId;
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

        // --- (1) Chuẩn hoá idempotencyKey theo method ---
        String methodPart = req.getMethod() != null ? req.getMethod().name() : PaymentMethod.COD.name();
        String normalizedIdem = req.getIdempotencyKey();
        if (normalizedIdem == null || normalizedIdem.isBlank()) {
            normalizedIdem = "auto-" + methodPart;
        } else {
            normalizedIdem = methodPart + ":" + normalizedIdem;
        }
        req.setIdempotencyKey(normalizedIdem);

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

        // 2) Idempotency bằng Redis + Lua — theo cặp (orderId, normalizedIdem)
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            String key = "idem:payment:" + orderId + ":" + req.getIdempotencyKey();
            log.info("[IDEMP] try SETNX key={} ttlSec={}", key, IDEM_TTL.toSeconds());
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_IDEM_SET_IF_ABSENT, Long.class);
            Long ok = redis.execute(script, List.of(key), String.valueOf(IDEM_TTL.toSeconds()));
            log.info("[IDEMP] result ok={} (1=new, 0=duplicate)", ok);

            if (ok == null || ok == 0L) {
                // Duplicate trong TTL → ưu tiên lấy theo idempotencyKey, rồi mới fallback orderId
                Payment dup = paymentRepo.findByIdempotencyKey(req.getIdempotencyKey())
                        .orElseGet(() -> paymentRepo.findByOrderId(orderId)
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.CONFLICT, "Duplicate payment request for orderId=" + orderId)));

                log.info("[IDEMP] duplicate-hit -> existing id={} status={} method={} provider={} amount={} idemKey={}",
                        dup.getId(), dup.getStatus(), dup.getMethod(), dup.getProvider(), dup.getAmount(), dup.getIdempotencyKey());

                if (dup.getStatus() == PaymentStatus.PENDING) {
                    if (dup.getMethod() != req.getMethod()) {
                        log.info("[IDEMP] method differs ({} vs {}), return existing without reconcile", dup.getMethod(), req.getMethod());
                        log.info("[RETURN] id={} orderId={} status={} method={} provider={} amount={} idemKey={}",
                                dup.getId(), dup.getOrderId(), dup.getStatus(), dup.getMethod(), dup.getProvider(), dup.getAmount(), dup.getIdempotencyKey());
                        return dup;
                    }

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

        // 4) Reconcile khi còn PENDING (giữ an toàn: không đổi method nếu khác)
        if (p.getStatus() == PaymentStatus.PENDING) {
            if (p.getMethod() != null && req.getMethod() != null && p.getMethod() != req.getMethod()) {
                log.info("[RECON] skip reconcile due to method change {} -> {}", p.getMethod(), req.getMethod());
            } else {
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
        }

        // Nếu đã xử lý rồi → trả luôn
        if (p.getStatus() != PaymentStatus.PENDING && p.getMethod() != PaymentMethod.PAYPAL) {
            log.info("[RETURN] id={} orderId={} status={} method={} provider={} amount={} idemKey={}",
                    p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getProvider(), p.getAmount(), p.getIdempotencyKey());
            return p;
        }

        // 5) Xử lý thanh toán
        if (req.getMethod() == PaymentMethod.COD) {
            log.info("[RETURN] PENDING(COD) id={} orderId={} status={} method={} amount={} idemKey={}",
                    p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getAmount(), p.getIdempotencyKey());
            return p;
        }
        Map<String, String> params = new HashMap<>();
        if (req.getReturnUrl() != null) params.put("returnUrl", req.getReturnUrl());

        PaymentProcessor processor = processors.get(p.getMethod());
        PaymentResult result = (processor != null)
                ? processor.charge(p, params)
                : PaymentResult.builder().success(true).transactionRef(null).build();

        // PAYPAL: create order -> giữ PENDING, lưu paypalOrderId + log approvalUrl
        if (p.getMethod() == PaymentMethod.PAYPAL) {
            p.setAmount(amount);
            p.setTransactionRef(result.getTransactionRef());
            paymentRepo.save(p);

            log.info("[PAYPAL] created order={} approvalUrl={}", result.getTransactionRef(), result.getApprovalUrl());
            log.info("[RETURN] id={} orderId={} status={} method={} provider={} amount={} idemKey={}",
                    p.getId(), p.getOrderId(), p.getStatus(), p.getMethod(), p.getProvider(), p.getAmount(), p.getIdempotencyKey());
            return p;
        }

        p.setAmount(amount);
        p.setTransactionRef(result.getTransactionRef());
        p.setStatus(result.isSuccess() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepo.save(p);
        log.info("[PROC ] online processed id={} status={} method={} amount={} txRef={}",
                p.getId(), p.getStatus(), p.getMethod(), p.getAmount(), p.getTransactionRef());

        // 6) Outbox
        try {
            boolean successFlag = (p.getStatus() == PaymentStatus.SUCCESS);
            if (successFlag) {
                var evt = PaymentSucceeded.builder()
                        .orderId(orderId)
                        .paymentId(p.getId())
                        .amount(p.getAmount())
                        .status(p.getStatus().name())
                        .email(email)
                        .method(p.getMethod().name())
                        .provider(p.getProvider())
                        // .transactionRef(p.getTransactionRef()) // bật nếu DTO có field này
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
    @Transactional
    public Payment capturePaypal(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paypalOrderId is required");
        }

        Payment p = paymentRepo.findByTransactionRef(paypalOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment with transactionRef=" + paypalOrderId + " not found"));

        Map<?, ?> body = payPalClient.capture(paypalOrderId);

        // PayPal trả status ở cấp order hoặc trong captures[]
        boolean completed = false;
        Object st = body.get("status");
        if (st instanceof String s && "COMPLETED".equalsIgnoreCase(s)) {
            completed = true;
        } else {
            Object pus = body.get("purchase_units");
            if (pus instanceof List<?> list && !list.isEmpty()) {
                Object pu0 = list.get(0);
                if (pu0 instanceof Map<?, ?> puMap) {
                    Object payments = puMap.get("payments");
                    if (payments instanceof Map<?, ?> payMap) {
                        Object captures = payMap.get("captures");
                        if (captures instanceof List<?> caps && !caps.isEmpty()) {
                            Object c0 = caps.get(0);
                            if (c0 instanceof Map<?, ?> cMap) {
                                Object cs = cMap.get("status");
                                if (cs instanceof String csStr && "COMPLETED".equalsIgnoreCase(csStr)) {
                                    completed = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        p.setStatus(completed ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        paymentRepo.save(p);

        log.info("[PAYPAL][CAP] order={} result={} paymentId={} status={}",
                paypalOrderId, completed ? "COMPLETED" : "FAILED", p.getId(), p.getStatus());

        try {
            boolean successFlag = (p.getStatus() == PaymentStatus.SUCCESS);
            if (successFlag) {
                var evt = PaymentSucceeded.builder()
                        .orderId(p.getOrderId())
                        .paymentId(p.getId())
                        .amount(p.getAmount())
                        .status(p.getStatus().name())
                        .email(null)
                        .method(p.getMethod().name())
                        .provider(p.getProvider())
                        // .transactionRef(p.getTransactionRef()) // bật nếu DTO có field này
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
                        .orderId(p.getOrderId())
                        .paymentId(p.getId())
                        .reason("PAYPAL_CAPTURE_FAILED")
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
            // tuỳ chọn: đổi status sang FAILED nếu serialize lỗi
            p.setStatus(PaymentStatus.FAILED);
            paymentRepo.save(p);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot serialize outbox event", e);
        }

        return p;
    }
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
                //.method(PaymentMethod.COD) // có thể bỏ set cứng nếu muốn nhận method từ client
                .build();
        return pay(req, authorizationHeader);
    }

    public Optional<Payment> findByOrderId(String orderId) {
        return paymentRepo.findByOrderId(orderId);
    }
}
