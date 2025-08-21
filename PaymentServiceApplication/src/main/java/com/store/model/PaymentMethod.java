package com.store.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum PaymentMethod {
    COD,           // Trả tiền khi nhận hàng
    MOMO,          // Ví MoMo
    VNPAY,         // Cổng VNPAY
    CREDIT_CARD,   // Thẻ (placeholder)
    BANK_TRANSFER; // Chuyển khoản (placeholder)

    @JsonCreator
    public static PaymentMethod from(Object v) {
        if (v == null) return null;
        String s = v.toString().trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "COD": return COD;
            case "MOMO": return MOMO;
            case "VNPAY": return VNPAY;
            case "CREDIT_CARD": return CREDIT_CARD;
            case "BANK_TRANSFER": return BANK_TRANSFER;
            default: throw new IllegalArgumentException("Unsupported payment method: " + v);
        }
    }
}
