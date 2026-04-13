package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.stock.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {
    List<StockMovement> findAllByOrderByCreatedAtDesc();
    List<StockMovement> findAllByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockMovement> findAllByClientIdOrderByCreatedAtDesc(Long clientId);
}
