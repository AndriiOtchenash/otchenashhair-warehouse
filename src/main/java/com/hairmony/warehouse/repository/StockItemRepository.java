package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.stock.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    List<StockItem> findAllByProductIdOrderByCreatedAtAsc(Long productId);

    @Query("SELECT si FROM StockItem si WHERE si.product.id = :productId AND si.quantity > 0 ORDER BY si.createdAt ASC")
    List<StockItem> findAvailableByProductIdFifo(@Param("productId") Long productId);

    @Query("SELECT COALESCE(SUM(si.quantity), 0) FROM StockItem si WHERE si.product.id = :productId AND si.quantity > 0")
    java.math.BigDecimal getTotalQuantityByProductId(@Param("productId") Long productId);

    @Query("SELECT si.product.id, COALESCE(SUM(si.quantity), 0) FROM StockItem si WHERE si.quantity > 0 GROUP BY si.product.id")
    List<Object[]> getTotalQuantityPerProduct();

    @Query("SELECT si FROM StockItem si WHERE si.product.id = :productId AND si.expiryDate IS NOT NULL AND si.quantity > 0 ORDER BY si.expiryDate ASC")
    List<StockItem> findEarliestExpiryByProductId(@Param("productId") Long productId);

    @Query("SELECT si.product.id, MIN(si.expiryDate) FROM StockItem si WHERE si.expiryDate IS NOT NULL AND si.quantity > 0 GROUP BY si.product.id")
    List<Object[]> findEarliestExpiryPerProduct();

    @Query("SELECT si FROM StockItem si WHERE si.expiryDate IS NOT NULL AND si.quantity > 0 AND si.expiryDate <= :date ORDER BY si.expiryDate ASC")
    List<StockItem> findExpiringBefore(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(si.quantity * si.purchasePrice), 0) FROM StockItem si WHERE si.quantity > 0 AND si.purchasePrice IS NOT NULL")
    java.math.BigDecimal getTotalStockValue();
}
