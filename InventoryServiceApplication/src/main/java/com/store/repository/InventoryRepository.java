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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
           SET i.quantity = i.quantity - :qty
         WHERE i.productId = :pid
           AND i.quantity >= :qty
           AND :qty > 0
    """)
    int reserve(@Param("pid") String productId, @Param("qty") int qty);

    // Cộng trả tồn, chỉ cộng khi qty > 0 (tránh truyền âm)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Inventory i
           SET i.quantity = i.quantity + :qty
         WHERE i.productId = :pid
           AND :qty > 0
    """)
    int release(@Param("pid") String productId, @Param("qty") int qty);
}