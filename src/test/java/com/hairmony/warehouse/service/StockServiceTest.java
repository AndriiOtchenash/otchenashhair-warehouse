package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.product.Product;
import com.hairmony.warehouse.domain.product.Unit;
import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.StockItem;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.repository.*;
import com.hairmony.warehouse.web.dto.StockExpenseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock StockItemRepository stockItemRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock ProductRepository productRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock ClientRepository clientRepository;
    @Mock UserRepository userRepository;
    @Mock MessageSource messageSource;

    @InjectMocks StockService stockService;

    @BeforeEach
    void setUpSecurity() {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken("testuser", null, List.of())
        ));
        lenient().when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers ----

    private Product product(Long id, Unit unit) {
        return Product.builder()
                .id(id).name("Test Product").unit(unit)
                .unitSize(BigDecimal.ONE).minStockLevel(BigDecimal.TEN)
                .active(true).build();
    }

    private StockItem stockItem(Long id, BigDecimal qty) {
        return StockItem.builder()
                .id(id).quantity(qty)
                .purchasePrice(new BigDecimal("50.00")).build();
    }

    private StockExpenseDto expenseDto(Long productId, BigDecimal qty, MovementType type) {
        StockExpenseDto dto = new StockExpenseDto();
        dto.setProductId(productId);
        dto.setQuantity(qty);
        dto.setMovementType(type);
        return dto;
    }

    // ========================
    // FIFO deduction
    // ========================

    @Test
    void registerExpense_singleBatch_exactQty_batchReachesZero() {
        Product p = product(1L, Unit.PCS);
        StockItem batch = stockItem(10L, new BigDecimal("5"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("5"));
        when(stockItemRepository.findAvailableByProductIdFifo(1L)).thenReturn(List.of(batch));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        stockService.registerExpense(expenseDto(1L, new BigDecimal("5"), MovementType.WRITE_OFF));

        assertThat(batch.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void registerExpense_singleBatch_partialQty_batchReducedByAmount() {
        Product p = product(1L, Unit.PCS);
        StockItem batch = stockItem(10L, new BigDecimal("10"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("10"));
        when(stockItemRepository.findAvailableByProductIdFifo(1L)).thenReturn(List.of(batch));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        stockService.registerExpense(expenseDto(1L, new BigDecimal("3"), MovementType.WRITE_OFF));

        assertThat(batch.getQuantity()).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    void registerExpense_multipleBatches_oldestConsumedFirst() {
        Product p = product(1L, Unit.PCS);
        StockItem older = stockItem(10L, new BigDecimal("3"));
        StockItem newer = stockItem(11L, new BigDecimal("10"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("13"));
        when(stockItemRepository.findAvailableByProductIdFifo(1L)).thenReturn(List.of(older, newer));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // deduct 5: older batch (3) fully consumed, newer reduced by 2
        stockService.registerExpense(expenseDto(1L, new BigDecimal("5"), MovementType.WRITE_OFF));

        assertThat(older.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(newer.getQuantity()).isEqualByComparingTo(new BigDecimal("8"));
    }

    @Test
    void registerExpense_multipleBatches_exactlyFirstBatch_secondUntouched() {
        Product p = product(1L, Unit.PCS);
        StockItem first = stockItem(10L, new BigDecimal("5"));
        StockItem second = stockItem(11L, new BigDecimal("10"));

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("15"));
        when(stockItemRepository.findAvailableByProductIdFifo(1L)).thenReturn(List.of(first, second));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        stockService.registerExpense(expenseDto(1L, new BigDecimal("5"), MovementType.WRITE_OFF));

        assertThat(first.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(second.getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void registerExpense_insufficientStock_throwsIllegalState() {
        Product p = product(1L, Unit.PCS);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("2"));
        when(messageSource.getMessage(eq("stock.expense.insufficientStock"), any(), any()))
                .thenReturn("Insufficient stock");

        assertThatThrownBy(() -> stockService.registerExpense(
                expenseDto(1L, new BigDecimal("5"), MovementType.WRITE_OFF)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========================
    // Movement cancellation — guards
    // ========================

    @Test
    void cancelMovement_ofCancellationMovement_throws() {
        // A movement that is itself a cancellation (has originalMovementId) cannot be cancelled
        StockMovement cancellation = StockMovement.builder()
                .id(99L).originalMovementId(1L).createdAt(LocalDateTime.now()).build();
        when(stockMovementRepository.findById(99L)).thenReturn(Optional.of(cancellation));
        when(messageSource.getMessage(eq("movement.cancel.error.notAllowed"), any(), any()))
                .thenReturn("Not allowed");

        assertThatThrownBy(() -> stockService.cancelMovement(99L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelMovement_alreadyCancelled_throws() {
        Product p = product(1L, Unit.PCS);
        StockMovement sale = StockMovement.builder()
                .id(1L).product(p).movementType(MovementType.SALE)
                .quantity(new BigDecimal("3")).createdAt(LocalDateTime.now()).build();
        when(stockMovementRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(stockMovementRepository.existsByOriginalMovementId(1L)).thenReturn(true);
        when(messageSource.getMessage(eq("movement.cancel.error.alreadyCancelled"), any(), any()))
                .thenReturn("Already cancelled");

        assertThatThrownBy(() -> stockService.cancelMovement(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelMovement_purchasePartiallyUsed_throws() {
        Product p = product(1L, Unit.PCS);
        // Original purchase qty was 10, but only 7 remain in the batch (3 sold)
        StockItem batch = stockItem(5L, new BigDecimal("7"));
        StockMovement purchase = StockMovement.builder()
                .id(4L).product(p).movementType(MovementType.PURCHASE)
                .quantity(new BigDecimal("10")).stockItem(batch)
                .createdAt(LocalDateTime.now()).build();

        when(stockMovementRepository.findById(4L)).thenReturn(Optional.of(purchase));
        when(stockMovementRepository.existsByOriginalMovementId(4L)).thenReturn(false);
        when(messageSource.getMessage(eq("movement.cancel.error.purchasePartiallyUsed"), any(), any()))
                .thenReturn("Partially used");

        assertThatThrownBy(() -> stockService.cancelMovement(4L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelMovement_purchaseWithNullBatch_throws() {
        Product p = product(1L, Unit.PCS);
        StockMovement purchase = StockMovement.builder()
                .id(5L).product(p).movementType(MovementType.PURCHASE)
                .quantity(new BigDecimal("10")).stockItem(null)
                .createdAt(LocalDateTime.now()).build();

        when(stockMovementRepository.findById(5L)).thenReturn(Optional.of(purchase));
        when(stockMovementRepository.existsByOriginalMovementId(5L)).thenReturn(false);
        when(messageSource.getMessage(eq("movement.cancel.error.purchasePartiallyUsed"), any(), any()))
                .thenReturn("Partially used");

        assertThatThrownBy(() -> stockService.cancelMovement(5L))
                .isInstanceOf(IllegalStateException.class);
    }

    // ========================
    // Movement cancellation — success paths
    // ========================

    @Test
    void cancelMovement_sale_createsRestoredStockItemWithOriginalQty() {
        Product p = product(1L, Unit.PCS);
        StockItem originalBatch = stockItem(5L, new BigDecimal("10"));
        StockMovement sale = StockMovement.builder()
                .id(2L).product(p).movementType(MovementType.SALE)
                .quantity(new BigDecimal("3")).unitPrice(new BigDecimal("100"))
                .stockItem(originalBatch).createdAt(LocalDateTime.now()).build();

        when(stockMovementRepository.findById(2L)).thenReturn(Optional.of(sale));
        when(stockMovementRepository.existsByOriginalMovementId(2L)).thenReturn(false);
        when(stockItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("13"));

        StockService.CancelResult result = stockService.cancelMovement(2L);

        assertThat(result.isPurchase()).isFalse();
        assertThat(result.productName()).isEqualTo("Test Product");
        assertThat(result.newStockFormatted()).isEqualTo("13");

        ArgumentCaptor<StockItem> itemCaptor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void cancelMovement_purchase_zeroesOriginalBatch() {
        Product p = product(1L, Unit.PCS);
        StockItem batch = stockItem(5L, new BigDecimal("10"));
        StockMovement purchase = StockMovement.builder()
                .id(3L).product(p).movementType(MovementType.PURCHASE)
                .quantity(new BigDecimal("10")).unitPrice(new BigDecimal("50"))
                .stockItem(batch).createdAt(LocalDateTime.now()).build();

        when(stockMovementRepository.findById(3L)).thenReturn(Optional.of(purchase));
        when(stockMovementRepository.existsByOriginalMovementId(3L)).thenReturn(false);
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(BigDecimal.ZERO);

        StockService.CancelResult result = stockService.cancelMovement(3L);

        assertThat(result.isPurchase()).isTrue();
        assertThat(batch.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        // No new StockItem created for purchase cancellation
        verify(stockItemRepository, never()).save(any());
    }

    @Test
    void cancelMovement_purchase_cancellationMovementLinkedToOriginal() {
        Product p = product(1L, Unit.PCS);
        StockItem batch = stockItem(5L, new BigDecimal("10"));
        StockMovement purchase = StockMovement.builder()
                .id(3L).product(p).movementType(MovementType.PURCHASE)
                .quantity(new BigDecimal("10")).unitPrice(new BigDecimal("50"))
                .stockItem(batch).createdAt(LocalDateTime.now()).build();

        when(stockMovementRepository.findById(3L)).thenReturn(Optional.of(purchase));
        when(stockMovementRepository.existsByOriginalMovementId(3L)).thenReturn(false);
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(BigDecimal.ZERO);

        ArgumentCaptor<StockMovement> movCaptor = ArgumentCaptor.forClass(StockMovement.class);
        when(stockMovementRepository.save(movCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        stockService.cancelMovement(3L);

        StockMovement saved = movCaptor.getValue();
        assertThat(saved.getMovementType()).isEqualTo(MovementType.CANCELLATION);
        assertThat(saved.getOriginalMovementId()).isEqualTo(3L);
    }

    // ========================
    // getCancelledMovementIds
    // ========================

    @Test
    void getCancelledMovementIds_emptyInput_returnsEmptyWithoutQuery() {
        Set<Long> result = stockService.getCancelledMovementIds(Set.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void getCancelledMovementIds_nonEmpty_delegatesToRepository() {
        Set<Long> ids = Set.of(1L, 2L, 3L);
        when(stockMovementRepository.findCancelledMovementIds(ids)).thenReturn(Set.of(2L));

        Set<Long> result = stockService.getCancelledMovementIds(ids);

        assertThat(result).containsExactly(2L);
    }
}
