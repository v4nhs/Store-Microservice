package com.store.repository;

import com.store.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserIdOrderByIdDesc(String userId);

}
