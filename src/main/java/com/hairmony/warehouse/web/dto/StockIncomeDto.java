package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIncomeDto {

    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin(value = "0.001")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal purchasePrice;

    @NotNull
    private Long supplierId;

    private LocalDate expiryDate;
    private String batchNumber;
    private String notes;
}
