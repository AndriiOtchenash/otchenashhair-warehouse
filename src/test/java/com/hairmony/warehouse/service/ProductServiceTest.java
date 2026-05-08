package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.product.Product;
import com.hairmony.warehouse.domain.product.Unit;
import com.hairmony.warehouse.repository.ProductRepository;
import com.hairmony.warehouse.repository.StockItemRepository;
import com.hairmony.warehouse.web.dto.ProductLookupDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock StockItemRepository stockItemRepository;

    @InjectMocks ProductService productService;

    // ---- helpers ----

    private Product product(Long id, String barcode) {
        return Product.builder()
                .id(id).name("Test Product").brand("Brand")
                .unit(Unit.PCS).unitSize(BigDecimal.ONE)
                .minStockLevel(new BigDecimal("5"))
                .barcode(barcode).active(true).build();
    }

    // ========================
    // findByBarcode — availableQty
    // ========================

    @Test
    void findByBarcode_productWithStock_returnsPositiveAvailableQty() {
        Product p = product(1L, "1234567890");
        when(productRepository.findByBarcode("1234567890")).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("10"));

        Optional<ProductLookupDto> result = productService.findByBarcode("1234567890");

        assertThat(result).isPresent();
        assertThat(result.get().getAvailableQty()).isEqualByComparingTo("10");
    }

    @Test
    void findByBarcode_productWithNoStock_returnsZeroAvailableQty() {
        Product p = product(1L, "1234567890");
        when(productRepository.findByBarcode("1234567890")).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(BigDecimal.ZERO);

        Optional<ProductLookupDto> result = productService.findByBarcode("1234567890");

        assertThat(result).isPresent();
        assertThat(result.get().getAvailableQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void findByBarcode_unknownBarcode_returnsEmpty() {
        when(productRepository.findByBarcode("unknown")).thenReturn(Optional.empty());

        Optional<ProductLookupDto> result = productService.findByBarcode("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void findByBarcode_mapsAllFields() {
        Product p = product(1L, "1234567890");
        when(productRepository.findByBarcode("1234567890")).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("3"));

        ProductLookupDto dto = productService.findByBarcode("1234567890").orElseThrow();

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Test Product");
        assertThat(dto.getBrand()).isEqualTo("Brand");
        assertThat(dto.getUnit()).isEqualTo("PCS");
        assertThat(dto.getBarcode()).isEqualTo("1234567890");
        assertThat(dto.getAvailableQty()).isEqualByComparingTo("3");
    }

    // ========================
    // searchForScan — availableQty
    // ========================

    @Test
    void searchForScan_returnsAvailableQtyForEachProduct() {
        Product p1 = product(1L, "111");
        Product p2 = product(2L, "222");
        when(productRepository.findAllByActiveTrueAndNameContainingIgnoreCase("shampoo"))
                .thenReturn(List.of(p1, p2));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("5"));
        when(stockItemRepository.getTotalQuantityByProductId(2L)).thenReturn(BigDecimal.ZERO);

        List<ProductLookupDto> results = productService.searchForScan("shampoo");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAvailableQty()).isEqualByComparingTo("5");
        assertThat(results.get(1).getAvailableQty()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========================
    // assignBarcode — availableQty included in result
    // ========================

    @Test
    void assignBarcode_resultIncludesAvailableQty() {
        Product p = product(1L, null);
        when(productRepository.findByBarcode("9999")).thenReturn(Optional.empty());
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        when(stockItemRepository.getTotalQuantityByProductId(1L)).thenReturn(new BigDecimal("7"));

        ProductLookupDto result = productService.assignBarcode(1L, "9999");

        assertThat(result.getAvailableQty()).isEqualByComparingTo("7");
    }

    @Test
    void assignBarcode_barcodeAlreadyUsedByOtherProduct_throws() {
        Product other = product(99L, "9999");
        when(productRepository.findByBarcode("9999")).thenReturn(Optional.of(other));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> productService.assignBarcode(1L, "9999"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
