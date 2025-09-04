package com.store.repository;


import com.store.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(String orderId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByTransactionRef(String transactionRef);

}