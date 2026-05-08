package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.stock.*;
import com.hairmony.warehouse.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final StockMovementRepository movementRepository;
    private final StockItemRepository stockItemRepository;

    // Earliest movement date for ALL_TIME preset
    public LocalDate getEarliestMovementDate() {
        return movementRepository.findEarliestMovementDate()
                .map(dt -> dt.toLocalDate())
                .orElse(LocalDate.now().withDayOfMonth(1));
    }

    // Expiry alerts
    public List<StockItem> getExpiringItems(int days) {
        return stockItemRepository.findExpiringBefore(LocalDate.now().plusDays(days));
    }

    // Summary KPI banner: revenue, purchases, gross profit, margin%, sales count
    public Map<String, Object> getReportSummary(LocalDate from, LocalDate to) {
        List<StockMovement> sales = movementRepository.findSalesBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));
        List<StockMovement> purchases = movementRepository.findPurchasesBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        BigDecimal totalRevenue = sales.stream()
                .filter(m -> m.getUnitPrice() != null)
                .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCOGS = sales.stream()
                .filter(m -> m.getStockItem() != null && m.getStockItem().getPurchasePrice() != null)
                .map(m -> m.getQuantity().multiply(m.getStockItem().getPurchasePrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPurchases = purchases.stream()
                .filter(m -> m.getUnitPrice() != null)
                .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossProfit = totalRevenue.subtract(totalCOGS);
        BigDecimal marginPct = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalPurchases", totalPurchases);
        summary.put("grossProfit", grossProfit);
        summary.put("marginPct", marginPct);
        summary.put("salesCount", sales.size());
        return summary;
    }

    // Current stock value snapshot
    public BigDecimal getStockValue() {
        return stockItemRepository.getTotalStockValue();
    }

    // Top sales by product, with salesCount and share %
    public List<Map<String, Object>> getTopSales(LocalDate from, LocalDate to) {
        List<StockMovement> sales = movementRepository.findSalesBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        BigDecimal grandTotal = sales.stream()
                .filter(m -> m.getUnitPrice() != null)
                .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sales.stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<StockMovement> group = e.getValue();
                    StockMovement first = group.get(0);
                    BigDecimal totalQty = group.stream()
                            .map(StockMovement::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalRevenue = group.stream()
                            .filter(m -> m.getUnitPrice() != null)
                            .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal sharePct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                            ? totalRevenue.divide(grandTotal, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productName", first.getProduct().getName());
                    row.put("unit", first.getProduct().getUnit().name());
                    row.put("totalQty", totalQty);
                    row.put("totalRevenue", totalRevenue);
                    row.put("salesCount", group.size());
                    row.put("sharePct", sharePct);
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalRevenue"))
                        .compareTo((BigDecimal) a.get("totalRevenue")))
                .toList();
    }

    // Write-offs summary: by product + total estimated loss
    public Map<String, Object> getWriteOffsSummary(LocalDate from, LocalDate to) {
        List<StockMovement> writeOffs = movementRepository.findWriteOffsBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        BigDecimal totalLoss = writeOffs.stream()
                .filter(m -> m.getStockItem() != null && m.getStockItem().getPurchasePrice() != null)
                .map(m -> m.getQuantity().multiply(m.getStockItem().getPurchasePrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> byProduct = writeOffs.stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<StockMovement> group = e.getValue();
                    StockMovement first = group.get(0);
                    BigDecimal totalQty = group.stream()
                            .map(StockMovement::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal loss = group.stream()
                            .filter(m -> m.getStockItem() != null && m.getStockItem().getPurchasePrice() != null)
                            .map(m -> m.getQuantity().multiply(m.getStockItem().getPurchasePrice()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productName", first.getProduct().getName());
                    row.put("unit", first.getProduct().getUnit().name());
                    row.put("totalQty", totalQty);
                    row.put("loss", loss);
                    row.put("count", group.size());
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalQty")).compareTo((BigDecimal) a.get("totalQty")))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byProduct", byProduct);
        result.put("totalLoss", totalLoss);
        result.put("count", writeOffs.size());
        return result;
    }

    // Top clients by total spent in period
    public List<Map<String, Object>> getTopClients(LocalDate from, LocalDate to) {
        List<StockMovement> sales = movementRepository.findSalesBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        return sales.stream()
                .filter(m -> m.getClient() != null)
                .collect(Collectors.groupingBy(m -> m.getClient().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<StockMovement> group = e.getValue();
                    StockMovement first = group.get(0);
                    BigDecimal totalSpent = group.stream()
                            .filter(m -> m.getUnitPrice() != null)
                            .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("clientName", first.getClient().getName());
                    row.put("clientId", first.getClient().getId());
                    row.put("salesCount", group.size());
                    row.put("totalSpent", totalSpent);
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("totalSpent")).compareTo((BigDecimal) a.get("totalSpent")))
                .toList();
    }

    // Purchases summary
    public List<Map<String, Object>> getPurchasesSummary(LocalDate from, LocalDate to) {
        List<StockMovement> purchases = movementRepository.findPurchasesBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        BigDecimal totalSpent = purchases.stream()
                .filter(m -> m.getUnitPrice() != null)
                .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> bySupplier = purchases.stream()
                .filter(m -> m.getSupplier() != null)
                .collect(Collectors.groupingBy(m -> m.getSupplier().getName()))
                .entrySet().stream()
                .map(e -> {
                    BigDecimal supplierTotal = e.getValue().stream()
                            .filter(m -> m.getUnitPrice() != null)
                            .map(m -> m.getQuantity().multiply(m.getUnitPrice()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("supplierName", e.getKey());
                    row.put("total", supplierTotal);
                    row.put("count", e.getValue().size());
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total")))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSpent", totalSpent);
        result.put("bySupplier", bySupplier);
        result.put("count", purchases.size());
        return List.of(result);
    }

    // Margin analysis
    public List<Map<String, Object>> getMarginAnalysis(LocalDate from, LocalDate to) {
        List<StockMovement> sales = movementRepository.findSalesBetween(
                from.atStartOfDay(), to.atTime(23, 59, 59));

        return sales.stream()
                .filter(m -> m.getUnitPrice() != null && m.getStockItem() != null
                        && m.getStockItem().getPurchasePrice() != null)
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<StockMovement> group = e.getValue();
                    StockMovement first = group.get(0);
                    BigDecimal avgSalePrice = group.stream()
                            .map(StockMovement::getUnitPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP);
                    BigDecimal avgPurchasePrice = group.stream()
                            .map(m -> m.getStockItem().getPurchasePrice())
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(group.size()), 2, RoundingMode.HALF_UP);
                    BigDecimal margin = avgSalePrice.subtract(avgPurchasePrice);
                    BigDecimal marginPct = avgPurchasePrice.compareTo(BigDecimal.ZERO) > 0
                            ? margin.divide(avgPurchasePrice, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productName", first.getProduct().getName());
                    row.put("avgPurchasePrice", avgPurchasePrice);
                    row.put("avgSalePrice", avgSalePrice);
                    row.put("margin", margin);
                    row.put("marginPct", marginPct);
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("marginPct"))
                        .compareTo((BigDecimal) a.get("marginPct")))
                .toList();
    }
}
