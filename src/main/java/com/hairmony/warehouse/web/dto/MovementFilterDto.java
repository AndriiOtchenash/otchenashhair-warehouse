package com.hairmony.warehouse.web.dto;

import com.hairmony.warehouse.domain.stock.MovementType;
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
    private Long productId;
    private String productName;
    private String counterparty;
    private int page = 0;
}
