package com.hairmony.warehouse.web.dto;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.WriteOffReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class MovementFilterDto {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private MovementType movementType;
    private WriteOffReason writeOffReason;
    private Long productId;

    @Size(max = 200)
    private String productName;

    @Size(max = 200)
    private String counterparty;

    @Min(0)
    private int page = 0;
}
