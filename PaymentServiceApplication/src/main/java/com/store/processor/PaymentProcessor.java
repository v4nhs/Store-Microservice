package com.store.processor;

import com.store.model.Payment;
import com.store.model.PaymentMethod;
import com.store.model.PaymentResult;

import java.util.Map;

public interface PaymentProcessor {
    PaymentMethod method();

    PaymentResult charge(Payment payment, Map<String, String> params);
}