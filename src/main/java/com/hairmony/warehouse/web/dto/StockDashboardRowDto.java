package com.hairmony.warehouse.web.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDashboardRowDto {
    private Long productId;
    private String productName;
    private String brand;
    private String categoryName;
    private String unit;
    private BigDecimal currentQuantity;
    private BigDecimal minStockLevel;
    private String description;
    private LocalDate nearestExpiryDate;

    public StockStatus getStatus() {
        if (currentQuantity.compareTo(BigDecimal.ZERO) == 0) return StockStatus.OUT;
        if (currentQuantity.compareTo(minStockLevel) <= 0) return StockStatus.LOW;
        return StockStatus.OK;
    }

    public enum StockStatus { OK, LOW, OUT }
}
