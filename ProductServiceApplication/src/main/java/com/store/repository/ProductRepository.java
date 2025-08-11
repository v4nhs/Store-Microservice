package com.store.repository;

import com.store.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {
    @Modifying @Query("UPDATE Product p SET p.quantity = p.quantity - :qty WHERE p.id = :pid AND p.quantity >= :qty")
    int reserve(@Param("pid") String productId, @Param("qty") int qty);
    @Modifying @Query("UPDATE Product p SET p.quantity = p.quantity + :qty WHERE p.id = :pid")
    int release(@Param("pid") String productId, @Param("qty") int qty);

}
