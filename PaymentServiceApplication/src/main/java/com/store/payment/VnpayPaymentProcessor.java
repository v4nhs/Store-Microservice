package com.store.payment;

import com.store.model.Payment;
import com.store.model.PaymentMethod;
import com.store.model.PaymentProcessor;
import com.store.model.PaymentResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class VnpayPaymentProcessor implements PaymentProcessor {
    @Override public PaymentMethod method() { return PaymentMethod.VNPAY; }

    @Override
    public PaymentResult charge(Payment payment, Map<String, String> params) {
        // TODO: Gọi VNPAY real API
        return PaymentResult.builder()
                .success(true)
                .transactionRef("VNPAY-" + UUID.randomUUID())
                .build();
    }
}