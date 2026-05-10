package com.hairmony.warehouse.web.dto;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.WriteOffReason;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockExpenseDto {

    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin(value = "0.001")
    private BigDecimal quantity;

    @NotNull
    private MovementType movementType; // SALE, WRITE_OFF, ADJUSTMENT

    private BigDecimal unitPrice;       // only for SALE
    private Long clientId;              // only for SALE
    private WriteOffReason writeOffReason; // only for WRITE_OFF
    private String notes;
}
