package com.hairmony.warehouse.clientcare.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TopClientFinanceDto {
    private final Long clientId;
    private final String clientName;
    private final int visitCount;
    private final BigDecimal totalSpent;
}
