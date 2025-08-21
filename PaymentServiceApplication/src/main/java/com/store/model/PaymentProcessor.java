package com.store.model;

import java.util.Map;

public interface PaymentProcessor {
    PaymentMethod method();

    PaymentResult charge(Payment payment, Map<String, String> params);
}