package com.store.processor;

import com.store.model.Payment;
import com.store.model.PaymentMethod;
import com.store.model.PaymentResult;
import com.store.client.PayPalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaypalPaymentProcessor implements PaymentProcessor {

    private final PayPalClient paypal;

    @Value("${paypal.return-url:http://localhost:8086/paypal/return}")
    private String defaultReturnUrl;

    @Value("${paypal.cancel-url:http://localhost:8086/paypal/cancel}")
    private String defaultCancelUrl;

    @Override public PaymentMethod method() { return PaymentMethod.PAYPAL; }

    @Override
    public PaymentResult charge(Payment payment, Map<String,String> params) {
        String returnUrl = params.getOrDefault("returnUrl", defaultReturnUrl);
        String cancelUrl  = defaultCancelUrl;

        var created = paypal.createOrder(
                payment.getAmount(), "USD", returnUrl, cancelUrl, payment.getOrderId());

        return PaymentResult.builder()
                .success(false)
                .transactionRef(created.orderId())
                .approvalUrl(created.approvalUrl())
                .build();
    }
}
