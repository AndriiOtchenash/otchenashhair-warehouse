package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.stock.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findAllByOrderByCreatedAtDesc();
    List<StockMovement> findAllByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockMovement> findAllByClientIdOrderByCreatedAtDesc(Long clientId);
}
