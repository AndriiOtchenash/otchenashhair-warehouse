package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.product.Product;
import com.hairmony.warehouse.domain.product.Unit;
import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.StockItem;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.domain.supplier.Supplier;
import com.hairmony.warehouse.repository.StockItemRepository;
import com.hairmony.warehouse.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock StockMovementRepository movementRepository;
    @Mock StockItemRepository stockItemRepository;

    @InjectMocks ReportService reportService;

    // ---- helpers ----

    private Product product(Long id, String name) {
        return Product.builder().id(id).name(name).unit(Unit.PCS)
                .unitSize(BigDecimal.ONE).minStockLevel(BigDecimal.TEN).active(true).build();
    }

    private Client client(Long id, String name) {
        Client c = new Client();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Supplier supplier(Long id, String name) {
        Supplier s = new Supplier();
        s.setId(id);
        s.setName(name);
        return s;
    }

    private StockItem stockItem(BigDecimal purchasePrice) {
        return StockItem.builder().purchasePrice(purchasePrice).quantity(BigDecimal.ONE).build();
    }

    private StockMovement sale(Product p, BigDecimal qty, BigDecimal unitPrice, StockItem batch) {
        return StockMovement.builder()
                .product(p).movementType(MovementType.SALE)
                .quantity(qty).unitPrice(unitPrice).stockItem(batch).build();
    }

    private StockMovement sale(Product p, Client c, BigDecimal qty, BigDecimal unitPrice) {
        return StockMovement.builder()
                .product(p).client(c).movementType(MovementType.SALE)
                .quantity(qty).unitPrice(unitPrice).build();
    }

    private StockMovement purchase(Product p, Supplier s, BigDecimal qty, BigDecimal unitPrice) {
        return StockMovement.builder()
                .product(p).supplier(s).movementType(MovementType.PURCHASE)
                .quantity(qty).unitPrice(unitPrice).build();
    }

    // ========================
    // getReportSummary
    // ========================

    @Test
    void getReportSummary_revenue_sumOfSalePrices() {
        Product p = product(1L, "Shampoo");
        StockItem batch = stockItem(new BigDecimal("50"));
        // 2 units @ 100 + 3 units @ 80 = 200 + 240 = 440
        List<StockMovement> sales = List.of(
                sale(p, new BigDecimal("2"), new BigDecimal("100"), batch),
                sale(p, new BigDecimal("3"), new BigDecimal("80"), batch)
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);
        when(movementRepository.findPurchasesBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> summary = reportService.getReportSummary(LocalDate.now(), LocalDate.now());

        assertThat((BigDecimal) summary.get("totalRevenue")).isEqualByComparingTo("440");
    }

    @Test
    void getReportSummary_grossProfit_revenueMINUSCogs() {
        Product p = product(1L, "Shampoo");
        // sale: 10 units @ 100 each. Purchase price: 60/unit
        StockItem batch = stockItem(new BigDecimal("60"));
        List<StockMovement> sales = List.of(
                sale(p, new BigDecimal("10"), new BigDecimal("100"), batch)
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);
        when(movementRepository.findPurchasesBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> summary = reportService.getReportSummary(LocalDate.now(), LocalDate.now());

        // Revenue = 1000, COGS = 600, Gross Profit = 400
        assertThat((BigDecimal) summary.get("grossProfit")).isEqualByComparingTo("400");
    }

    @Test
    void getReportSummary_marginPct_calculatedFromRevenueAndCogs() {
        Product p = product(1L, "Shampoo");
        // Revenue = 1000, COGS = 600, Gross profit = 400, Margin = 40%
        StockItem batch = stockItem(new BigDecimal("60"));
        List<StockMovement> sales = List.of(
                sale(p, new BigDecimal("10"), new BigDecimal("100"), batch)
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);
        when(movementRepository.findPurchasesBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> summary = reportService.getReportSummary(LocalDate.now(), LocalDate.now());

        assertThat((BigDecimal) summary.get("marginPct")).isEqualByComparingTo("40.0");
    }

    @Test
    void getReportSummary_noSales_returnsZeros() {
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(List.of());
        when(movementRepository.findPurchasesBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> summary = reportService.getReportSummary(LocalDate.now(), LocalDate.now());

        assertThat((BigDecimal) summary.get("totalRevenue")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) summary.get("grossProfit")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((BigDecimal) summary.get("marginPct")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((Integer) summary.get("salesCount")).isZero();
    }

    @Test
    void getReportSummary_salesCount_equalsNumberOfSaleMovements() {
        Product p = product(1L, "Shampoo");
        StockItem batch = stockItem(new BigDecimal("50"));
        List<StockMovement> sales = List.of(
                sale(p, BigDecimal.ONE, new BigDecimal("100"), batch),
                sale(p, BigDecimal.ONE, new BigDecimal("100"), batch),
                sale(p, BigDecimal.ONE, new BigDecimal("100"), batch)
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);
        when(movementRepository.findPurchasesBetween(any(), any())).thenReturn(List.of());

        Map<String, Object> summary = reportService.getReportSummary(LocalDate.now(), LocalDate.now());

        assertThat((Integer) summary.get("salesCount")).isEqualTo(3);
    }

    // ========================
    // getTopSales
    // ========================

    @Test
    void getTopSales_groupsByProduct_sumsTotalRevenue() {
        Product p1 = product(1L, "Shampoo");
        Product p2 = product(2L, "Conditioner");
        StockItem batch = stockItem(new BigDecimal("50"));

        List<StockMovement> sales = List.of(
                sale(p1, new BigDecimal("2"), new BigDecimal("100"), batch),
                sale(p1, new BigDecimal("3"), new BigDecimal("100"), batch), // p1 total: 500
                sale(p2, new BigDecimal("1"), new BigDecimal("200"), batch)  // p2 total: 200
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);

        List<Map<String, Object>> top = reportService.getTopSales(LocalDate.now(), LocalDate.now());

        assertThat(top).hasSize(2);
        // Sorted by revenue desc — p1 (500) before p2 (200)
        assertThat(top.get(0).get("productName")).isEqualTo("Shampoo");
        assertThat((BigDecimal) top.get(0).get("totalRevenue")).isEqualByComparingTo("500");
        assertThat(top.get(1).get("productName")).isEqualTo("Conditioner");
    }

    @Test
    void getTopSales_sharePct_sumsTo100ForTwoProducts() {
        Product p1 = product(1L, "A");
        Product p2 = product(2L, "B");
        StockItem batch = stockItem(new BigDecimal("50"));

        List<StockMovement> sales = List.of(
                sale(p1, new BigDecimal("3"), new BigDecimal("100"), batch),  // 300 = 75%
                sale(p2, new BigDecimal("1"), new BigDecimal("100"), batch)   // 100 = 25%
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);

        List<Map<String, Object>> top = reportService.getTopSales(LocalDate.now(), LocalDate.now());

        BigDecimal shareA = (BigDecimal) top.stream()
                .filter(r -> r.get("productName").equals("A")).findFirst().orElseThrow().get("sharePct");
        BigDecimal shareB = (BigDecimal) top.stream()
                .filter(r -> r.get("productName").equals("B")).findFirst().orElseThrow().get("sharePct");

        assertThat(shareA).isEqualByComparingTo("75.0");
        assertThat(shareB).isEqualByComparingTo("25.0");
    }

    // ========================
    // getTopClients
    // ========================

    @Test
    void getTopClients_groupsByClient_sortedByTotalSpentDesc() {
        Product p = product(1L, "Shampoo");
        Client c1 = client(1L, "Alice");
        Client c2 = client(2L, "Bob");

        List<StockMovement> sales = List.of(
                sale(p, c1, new BigDecimal("2"), new BigDecimal("100")),  // Alice: 200
                sale(p, c2, new BigDecimal("5"), new BigDecimal("100")),  // Bob: 500
                sale(p, c1, new BigDecimal("1"), new BigDecimal("100"))   // Alice: +100 = 300
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);

        List<Map<String, Object>> clients = reportService.getTopClients(LocalDate.now(), LocalDate.now());

        assertThat(clients).hasSize(2);
        assertThat(clients.get(0).get("clientName")).isEqualTo("Bob");
        assertThat((BigDecimal) clients.get(0).get("totalSpent")).isEqualByComparingTo("500");
        assertThat(clients.get(1).get("clientName")).isEqualTo("Alice");
        assertThat((BigDecimal) clients.get(1).get("totalSpent")).isEqualByComparingTo("300");
    }

    @Test
    void getTopClients_salesWithoutClient_excluded() {
        Product p = product(1L, "Shampoo");
        Client c = client(1L, "Alice");

        // One sale with client, one without
        List<StockMovement> sales = List.of(
                sale(p, c, new BigDecimal("2"), new BigDecimal("100")),
                StockMovement.builder().product(p).movementType(MovementType.SALE)
                        .quantity(BigDecimal.ONE).unitPrice(new BigDecimal("200"))
                        .client(null).build()
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);

        List<Map<String, Object>> clients = reportService.getTopClients(LocalDate.now(), LocalDate.now());

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).get("clientName")).isEqualTo("Alice");
    }

    // ========================
    // getMarginAnalysis
    // ========================

    @Test
    void getMarginAnalysis_sortedByMarginPctDesc() {
        Product p1 = product(1L, "HighMargin");   // sell 200, buy 100 → 100% margin
        Product p2 = product(2L, "LowMargin");    // sell 110, buy 100 → 10% margin

        StockItem b1 = stockItem(new BigDecimal("100"));
        StockItem b2 = stockItem(new BigDecimal("100"));

        List<StockMovement> sales = List.of(
                sale(p1, BigDecimal.ONE, new BigDecimal("200"), b1),
                sale(p2, BigDecimal.ONE, new BigDecimal("110"), b2)
        );
        when(movementRepository.findSalesBetween(any(), any())).thenReturn(sales);

        List<Map<String, Object>> margin = reportService.getMarginAnalysis(LocalDate.now(), LocalDate.now());

        assertThat(margin.get(0).get("productName")).isEqualTo("HighMargin");
        assertThat((BigDecimal) margin.get(0).get("marginPct")).isEqualByComparingTo("100.0");
        assertThat(margin.get(1).get("productName")).isEqualTo("LowMargin");
        assertThat((BigDecimal) margin.get(1).get("marginPct")).isEqualByComparingTo("10.0");
    }
}
