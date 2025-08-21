package com.store.repository;

import com.store.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    @Query("select i from OrderItem i where i.order.id = :orderId and i.productId = :productId")
    Optional<OrderItem> findByOrderIdAndProductId(@Param("orderId") String orderId,
                                                  @Param("productId") String productId);
}

