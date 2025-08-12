package com.store.repository;

import com.store.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {
    Optional<Inventory> findByProductId(String productId);

    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty " +
            "WHERE i.productId = :productId AND i.quantity >= :qty")
    int applyReserve(@Param("productId") String productId, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :qty " +
            "WHERE i.productId = :productId")
    int applyRelease(@Param("productId") String productId, @Param("qty") int qty);
}