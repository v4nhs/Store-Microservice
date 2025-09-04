package com.store.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequest {
    @JsonAlias({"orderID", "order_id"})
    private String orderId;

    @JsonAlias({"totalAmount", "amount_value"})
    private BigDecimal amount;

    @JsonAlias({"idemKey", "idem_key"})
    private String idempotencyKey;
    @JsonAlias({"return_url"})
    private String returnUrl;
}