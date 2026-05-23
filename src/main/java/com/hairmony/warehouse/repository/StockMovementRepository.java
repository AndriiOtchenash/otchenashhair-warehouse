package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.stock.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {
    List<StockMovement> findAllByOrderByCreatedAtDesc();
    List<StockMovement> findAllByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockMovement> findAllByClientIdOrderByCreatedAtDesc(Long clientId);
    List<StockMovement> findAllBySupplierIdOrderByCreatedAtDesc(Long supplierId);
    boolean existsByOriginalMovementId(Long originalMovementId);
    boolean existsByClientId(Long clientId);
    boolean existsBySupplierId(Long supplierId);
    boolean existsByProductId(Long productId);

    @Query("SELECT DISTINCT m.client.id FROM StockMovement m WHERE m.client IS NOT NULL")
    Set<Long> findAllClientIdsWithMovements();

    @Query("SELECT DISTINCT m.supplier.id FROM StockMovement m WHERE m.supplier IS NOT NULL")
    Set<Long> findAllSupplierIdsWithMovements();

    @Query("SELECT m.originalMovementId FROM StockMovement m WHERE m.originalMovementId IN :ids")
    Set<Long> findCancelledMovementIds(@Param("ids") Set<Long> ids);

    @Query("SELECT m FROM StockMovement m WHERE m.movementType = 'SALE' AND m.createdAt >= :from AND m.createdAt <= :to ORDER BY m.createdAt DESC")
    List<StockMovement> findSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT m FROM StockMovement m WHERE m.movementType = 'PURCHASE' AND m.createdAt >= :from AND m.createdAt <= :to ORDER BY m.createdAt DESC")
    List<StockMovement> findPurchasesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT m FROM StockMovement m WHERE m.movementType = 'WRITE_OFF' AND m.createdAt >= :from AND m.createdAt <= :to ORDER BY m.createdAt DESC")
    List<StockMovement> findWriteOffsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT MIN(m.createdAt) FROM StockMovement m")
    Optional<LocalDateTime> findEarliestMovementDate();

    @Query("SELECT DISTINCT m.product.id FROM StockMovement m WHERE m.movementType = 'SALE' AND m.createdAt >= :from AND m.createdAt <= :to")
    Set<Long> findProductIdsWithSalesBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT m.client.id, m.client.name, m.client.phone,
                   MAX(m.createdAt), SUM(m.quantity * m.unitPrice)
            FROM StockMovement m
            WHERE m.movementType = 'SALE' AND m.client IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM StockMovement c
                  WHERE c.movementType = 'CANCELLATION' AND c.originalMovementId = m.id
              )
            GROUP BY m.client.id, m.client.name, m.client.phone
            ORDER BY MAX(m.createdAt) DESC
            """)
    List<Object[]> findClientSaleStats();
}
