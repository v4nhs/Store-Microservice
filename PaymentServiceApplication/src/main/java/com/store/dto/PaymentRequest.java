package com.store.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.store.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;


@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    @JsonAlias({"orderID", "order_id"})
    @NotBlank
    private String orderId;
    @NotNull
    @JsonProperty("method")
    @JsonAlias({"paymentMethod", "payment_method"})
    private PaymentMethod method;// COD/MOMO/VNPAY/...
    @NotBlank
    @JsonAlias({"idemKey", "idempotency_key"})
    private String idempotencyKey;
    @JsonAlias({"paymentProvider", "payment_provider"})
    private String provider;
    @JsonAlias({"return_url"})
    private String returnUrl;
    private BigDecimal amount;

}
