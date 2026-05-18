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
    @DecimalMin(value = "0.001", message = "{validation.quantity.positive}")
    private BigDecimal quantity;

    @NotNull
    private MovementType movementType; // SALE, WRITE_OFF, ADJUSTMENT

    private BigDecimal unitPrice;          // only for SALE
    private Long clientId;                 // only for SALE / WRITE_OFF+GIFT
    private WriteOffReason writeOffReason; // only for WRITE_OFF

    @Size(max = 1000, message = "{validation.size.max1000}")
    private String notes;

    /** SALE requires unitPrice > 0. */
    @AssertTrue(message = "{stock.expense.salePriceRequired}")
    public boolean isUnitPriceValidForSale() {
        if (movementType != MovementType.SALE) return true;
        return unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    /** WRITE_OFF requires a reason. */
    @AssertTrue(message = "{stock.expense.writeOffReasonRequired}")
    public boolean isWriteOffReasonRequired() {
        if (movementType != MovementType.WRITE_OFF) return true;
        return writeOffReason != null;
    }
}
