package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.product.Product;
import com.hairmony.warehouse.domain.stock.*;
import com.hairmony.warehouse.domain.user.User;
import com.hairmony.warehouse.repository.*;
import com.hairmony.warehouse.web.dto.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    public void registerIncome(StockIncomeDto dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + dto.getProductId()));

        StockItem stockItem = StockItem.builder()
                .product(product)
                .quantity(dto.getQuantity())
                .purchasePrice(dto.getPurchasePrice())
                .expiryDate(dto.getExpiryDate())
                .batchNumber(dto.getBatchNumber())
                .build();
        stockItemRepository.save(stockItem);

        StockMovement movement = StockMovement.builder()
                .product(product)
                .stockItem(stockItem)
                .movementType(MovementType.PURCHASE)
                .quantity(dto.getQuantity())
                .unitPrice(dto.getPurchasePrice())
                .supplier(supplierRepository.findById(dto.getSupplierId())
                        .orElseThrow(() -> new EntityNotFoundException("Supplier not found")))
                .notes(dto.getNotes())
                .performedBy(getCurrentUser())
                .build();
        stockMovementRepository.save(movement);
    }

    public void registerExpense(StockExpenseDto dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + dto.getProductId()));

        // Check total available
        BigDecimal available = stockItemRepository.getTotalQuantityByProductId(dto.getProductId());
        if (available.compareTo(dto.getQuantity()) < 0) {
            throw new IllegalStateException(messageSource.getMessage(
                    "stock.expense.insufficientStock", new Object[]{available}, LocaleContextHolder.getLocale()));
        }

        // SALE-specific price validation
        if (dto.getMovementType() == MovementType.SALE) {
            if (dto.getUnitPrice() == null || dto.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(messageSource.getMessage(
                        "stock.expense.salePriceRequired", null, LocaleContextHolder.getLocale()));
            }
        }

        // FIFO deduction
        BigDecimal remaining = dto.getQuantity();
        List<StockItem> batches = stockItemRepository.findAvailableByProductIdFifo(dto.getProductId());

        StockItem firstBatch = null;
        for (StockItem batch : batches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            if (firstBatch == null) firstBatch = batch;

            if (batch.getQuantity().compareTo(remaining) <= 0) {
                remaining = remaining.subtract(batch.getQuantity());
                batch.setQuantity(BigDecimal.ZERO);
            } else {
                batch.setQuantity(batch.getQuantity().subtract(remaining));
                remaining = BigDecimal.ZERO;
            }
        }

        StockMovement movement = StockMovement.builder()
                .product(product)
                .stockItem(firstBatch)
                .movementType(dto.getMovementType())
                .quantity(dto.getQuantity())
                .unitPrice(dto.getUnitPrice())
                .writeOffReason(dto.getMovementType() == MovementType.WRITE_OFF ? dto.getWriteOffReason() : null)
                .notes(dto.getNotes())
                .performedBy(getCurrentUser())
                .build();

        boolean needsClient = dto.getMovementType() == MovementType.SALE
                || (dto.getMovementType() == MovementType.WRITE_OFF && dto.getWriteOffReason() == WriteOffReason.GIFT);
        if (needsClient && dto.getClientId() != null) {
            movement.setClient(clientRepository.findById(dto.getClientId()).orElse(null));
        }

        stockMovementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getFifoPricesPerProduct() {
        Map<Long, BigDecimal> map = new HashMap<>();
        stockItemRepository.findFifoPricePerProduct()
                .forEach(row -> map.putIfAbsent((Long) row[0], (BigDecimal) row[1]));
        return map;
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
    public List<StockMovement> getMovementsByClient(Long clientId) {
        return stockMovementRepository.findAllByClientIdOrderByCreatedAtDesc(clientId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> getMovementsBySupplier(Long supplierId) {
        return stockMovementRepository.findAllBySupplierIdOrderByCreatedAtDesc(supplierId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> findAllMovements() {
        return stockMovementRepository.findAllByOrderByCreatedAtDesc();
    }

    public void updateStockItem(Long id, LocalDate expiryDate, String batchNumber, BigDecimal purchasePrice) {
        StockItem item = stockItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Stock item not found: " + id));
        item.setExpiryDate(expiryDate);
        item.setBatchNumber(batchNumber != null && !batchNumber.isBlank() ? batchNumber : null);
        item.setPurchasePrice(purchasePrice);
    }

    public record CancelResult(String productName, String qtyFormatted, String unitLabel,
                               String newStockFormatted, boolean isPurchase) {}

    public CancelResult cancelMovement(Long movementId) {
        StockMovement original = stockMovementRepository.findById(movementId)
                .orElseThrow(() -> new EntityNotFoundException("Movement not found: " + movementId));

        // A cancellation movement itself cannot be cancelled
        if (original.getOriginalMovementId() != null) {
            throw new IllegalStateException(messageSource.getMessage(
                    "movement.cancel.error.notAllowed", null, LocaleContextHolder.getLocale()));
        }

        // Cannot cancel if already cancelled
        if (stockMovementRepository.existsByOriginalMovementId(movementId)) {
            throw new IllegalStateException(messageSource.getMessage(
                    "movement.cancel.error.alreadyCancelled", null, LocaleContextHolder.getLocale()));
        }

        User currentUser = getCurrentUser();

        // Build note fields
        String productName = original.getProduct().getName();
        String unitLabel = switch (original.getProduct().getUnit()) {
            case ML -> "мл";
            case G -> "г";
            case PCS -> "шт";
        };
        BigDecimal qty = original.getQuantity();
        String qtyFormatted = (qty.scale() == 0 || qty.stripTrailingZeros().scale() <= 0)
                ? qty.toBigInteger().toString()
                : qty.stripTrailingZeros().toPlainString();
        String originalDate = original.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        StockItem stockItemForCancellation;
        String note;

        if (original.getMovementType() == MovementType.PURCHASE) {
            // Purchase cancellation: remove the stock batch that was created
            StockItem batch = original.getStockItem();
            if (batch == null || batch.getQuantity().compareTo(original.getQuantity()) != 0) {
                throw new IllegalStateException(messageSource.getMessage(
                        "movement.cancel.error.purchasePartiallyUsed", null, LocaleContextHolder.getLocale()));
            }
            batch.setQuantity(BigDecimal.ZERO);
            stockItemForCancellation = batch;
            note = "Скасування приходу: " + productName + ", " + qtyFormatted + " " + unitLabel + ", " + originalDate;
        } else {
            // Expense cancellation: restore stock by creating a new batch
            StockItem restored = StockItem.builder()
                    .product(original.getProduct())
                    .quantity(original.getQuantity())
                    .purchasePrice(original.getUnitPrice())
                    .build();
            stockItemRepository.save(restored);
            stockItemForCancellation = restored;
            note = "Скасування: " + productName + ", " + qtyFormatted + " " + unitLabel + ", " + originalDate;
        }

        StockMovement cancellation = StockMovement.builder()
                .product(original.getProduct())
                .stockItem(stockItemForCancellation)
                .movementType(MovementType.CANCELLATION)
                .quantity(original.getQuantity())
                .unitPrice(original.getUnitPrice())
                .supplier(original.getSupplier())
                .client(original.getClient())
                .notes(note)
                .originalMovementId(movementId)
                .performedBy(currentUser)
                .build();
        stockMovementRepository.save(cancellation);

        boolean isPurchase = original.getMovementType() == MovementType.PURCHASE;
        BigDecimal newStock = stockItemRepository.getTotalQuantityByProductId(original.getProduct().getId());
        String newStockFormatted = (newStock.scale() == 0 || newStock.stripTrailingZeros().scale() <= 0)
                ? newStock.toBigInteger().toString()
                : newStock.stripTrailingZeros().toPlainString();
        return new CancelResult(productName, qtyFormatted, unitLabel, newStockFormatted, isPurchase);
    }

    public void updateMovementMeta(Long movementId, Long clientId, String notes) {
        StockMovement movement = stockMovementRepository.findById(movementId)
                .orElseThrow(() -> new EntityNotFoundException("Movement not found: " + movementId));
        movement.setClient(clientId != null
                ? clientRepository.findById(clientId).orElse(null)
                : null);
        movement.setNotes(notes != null && !notes.isBlank() ? notes.strip() : null);
    }

    @Transactional(readOnly = true)
    public Set<Long> getCancelledMovementIds(Set<Long> movementIds) {
        if (movementIds.isEmpty()) return Set.of();
        return stockMovementRepository.findCancelledMovementIds(movementIds);
    }

    @Transactional(readOnly = true)
    public List<String> getDistinctBrands() {
        return productRepository.findDistinctBrands();
    }

    @Transactional(readOnly = true)
    public List<StockDashboardRowDto> getDashboard() {
        List<Product> products = productRepository.findAllByActiveTrue();

        Map<Long, BigDecimal> quantityMap = new HashMap<>();
        stockItemRepository.getTotalQuantityPerProduct()
                .forEach(row -> quantityMap.put((Long) row[0], (BigDecimal) row[1]));

        Map<Long, LocalDate> expiryMap = new HashMap<>();
        stockItemRepository.findEarliestExpiryPerProduct()
                .forEach(row -> expiryMap.put((Long) row[0], (LocalDate) row[1]));

        return products.stream().map(p -> StockDashboardRowDto.builder()
                .productId(p.getId())
                .productName(p.getName())
                .brand(p.getBrand())
                .categoryName(p.getCategory() != null ? p.getCategory().getName() : "")
                .unit(p.getUnit() != null ? p.getUnit().name() : "")
                .currentQuantity(quantityMap.getOrDefault(p.getId(), BigDecimal.ZERO))
                .minStockLevel(p.getMinStockLevel())
                .description(p.getDescription())
                .nearestExpiryDate(expiryMap.get(p.getId()))
                .build()
        ).toList();
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }
}
