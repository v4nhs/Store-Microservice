package com.store.processor;

import com.store.model.Payment;
import com.store.model.PaymentMethod;
import com.store.model.PaymentResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class MomoPaymentProcessor implements PaymentProcessor {
    @Override public PaymentMethod method() { return PaymentMethod.MOMO; }

    @Override
    public PaymentResult charge(Payment payment, Map<String, String> params) {
        return PaymentResult.builder()
                .success(true)
                .transactionRef("MOMO-" + UUID.randomUUID())
                .build();
    }
}