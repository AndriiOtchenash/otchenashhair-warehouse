package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.product.Product;
import com.hairmony.warehouse.domain.stock.*;
import com.hairmony.warehouse.repository.*;
import com.hairmony.warehouse.web.dto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ClientRepository clientRepository;

    public void registerIncome(StockIncomeDto dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + dto.getProductId()));

        // Create stock item (batch)
        StockItem stockItem = StockItem.builder()
                .product(product)
                .quantity(dto.getQuantity())
                .purchasePrice(dto.getPurchasePrice())
                .expiryDate(dto.getExpiryDate())
                .batchNumber(dto.getBatchNumber())
                .build();
        stockItemRepository.save(stockItem);

        // Record movement
        StockMovement movement = StockMovement.builder()
                .product(product)
                .stockItem(stockItem)
                .movementType(MovementType.PURCHASE)
                .quantity(dto.getQuantity())
                .unitPrice(dto.getPurchasePrice())
                .supplier(supplierRepository.findById(dto.getSupplierId())
                        .orElseThrow(() -> new EntityNotFoundException("Supplier not found")))
                .notes(dto.getNotes())
                .createdAt(LocalDateTime.now())
                .build();
        stockMovementRepository.save(movement);
    }

    public void registerExpense(StockExpenseDto dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + dto.getProductId()));

        // Check total available
        BigDecimal available = stockItemRepository.getTotalQuantityByProductId(dto.getProductId());
        if (available.compareTo(dto.getQuantity()) < 0) {
            throw new IllegalStateException("Insufficient stock. Available: " + available);
        }

        // FIFO deduction
        BigDecimal remaining = dto.getQuantity();
        List<StockItem> batches = stockItemRepository.findAvailableByProductIdFifo(dto.getProductId());

        for (StockItem batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            if (batch.getQuantity().compareTo(remaining) <= 0) {
                remaining = remaining.subtract(batch.getQuantity());
                batch.setQuantity(BigDecimal.ZERO);
            } else {
                batch.setQuantity(batch.getQuantity().subtract(remaining));
                remaining = BigDecimal.ZERO;
            }
        }

        // Record movement
        StockMovement movement = StockMovement.builder()
                .product(product)
                .movementType(dto.getMovementType())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .notes(dto.getNotes())
                .createdAt(LocalDateTime.now())
                .build();

        if (dto.getMovementType() == MovementType.SALE && dto.getClientId() != null) {
            movement.setClient(clientRepository.findById(dto.getClientId()).orElse(null));
        }

        stockMovementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAvailableQuantity(Long productId) {
        return stockItemRepository.getTotalQuantityByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<StockItem> getStockItemsByProduct(Long productId) {
        return stockItemRepository.findAllByProductIdOrderByCreatedAtAsc(productId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsByProduct(Long productId) {
        return stockMovementRepository.findAllByProductIdOrderByCreatedAtDesc(productId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> findAllMovements() {
        return stockMovementRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<StockDashboardRowDto> getDashboard() {
        List<Product> products = productRepository.findAllByActiveTrue();

        // Build quantity map
        Map<Long, BigDecimal> quantityMap = new HashMap<>();
        stockItemRepository.getTotalQuantityPerProduct()
                .forEach(row -> quantityMap.put((Long) row[0], (BigDecimal) row[1]));

        return products.stream().map(p -> {
            BigDecimal qty = quantityMap.getOrDefault(p.getId(), BigDecimal.ZERO);

            LocalDate nearestExpiry = stockItemRepository
                    .findEarliestExpiryByProductId(p.getId())
                    .stream()
                    .findFirst()
                    .map(si -> si.getExpiryDate())
                    .orElse(null);

            return StockDashboardRowDto.builder()
                    .productId(p.getId())
                    .productName(p.getName())
                    .brand(p.getBrand())
                    .categoryName(p.getCategory() != null ? p.getCategory().getName() : "")
                    .unit(p.getUnit() != null ? p.getUnit().name() : "")
                    .currentQuantity(qty)
                    .minStockLevel(p.getMinStockLevel())
                    .nearestExpiryDate(nearestExpiry)
                    .build();
        }).toList();
    }
}
