package com.store.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum PaymentMethod {
    COD,
    MOMO,
    PAYPAL;

    @JsonCreator
    public static PaymentMethod from(Object v) {
        if (v == null) return null;
        String s = v.toString().trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "COD": return COD;
            case "MOMO": return MOMO;
            case "PAYPAL": return PAYPAL;
            default: throw new IllegalArgumentException("Unsupported payment method: " + v);
        }
    }
}
