package com.store.repository;

import com.store.dto.ProductDTO;
import com.store.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
  UPDATE Product p
     SET p.quantity = p.quantity - :qty
   WHERE p.id = :pid
     AND p.quantity >= :qty
     AND :qty > 0
""")
    int reserve(@Param("pid") String productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
  UPDATE Product p
     SET p.quantity = p.quantity + :qty
   WHERE p.id = :pid
     AND :qty > 0
""")
    int release(@Param("pid") String productId, @Param("qty") int qty);
    Optional<Product> findTop1ByNameIgnoreCase(String name);
    Optional<Product> findTop1ByNameContainingIgnoreCase(String name);
    @Query(
            value = """
    SELECT * FROM product
     WHERE name COLLATE utf8mb4_0900_ai_ci LIKE CONCAT('%', :name, '%')
     LIMIT 1
  """,
            nativeQuery = true
    )
    Optional<Product> findOneAiCiLike(@Param("name") String name);

}
