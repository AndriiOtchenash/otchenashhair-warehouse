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
    @DecimalMin(value = "0.001", message = "{validation.quantity.positive}")
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false, message = "{validation.price.positive}")
    private BigDecimal purchasePrice;

    @NotNull
    private Long supplierId;

    private LocalDate expiryDate;

    @Size(max = 100, message = "{validation.size.max100}")
    private String batchNumber;

    @Size(max = 1000, message = "{validation.size.max1000}")
    private String notes;
}
