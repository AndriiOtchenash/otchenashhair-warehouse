package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.stock.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {
    List<StockMovement> findAllByOrderByCreatedAtDesc();
    List<StockMovement> findAllByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockMovement> findAllByClientIdOrderByCreatedAtDesc(Long clientId);

    @Query("SELECT m FROM StockMovement m WHERE m.movementType = 'SALE' AND m.createdAt >= :from AND m.createdAt <= :to ORDER BY m.createdAt DESC")
    List<StockMovement> findSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT m FROM StockMovement m WHERE m.movementType = 'PURCHASE' AND m.createdAt >= :from AND m.createdAt <= :to ORDER BY m.createdAt DESC")
    List<StockMovement> findPurchasesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
