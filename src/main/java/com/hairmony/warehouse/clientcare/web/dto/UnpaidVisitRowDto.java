package com.hairmony.warehouse.clientcare.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class UnpaidVisitRowDto {
    private final Long visitId;
    private final Long clientId;
    private final String clientName;
    private final LocalDate visitDate;
    private final String serviceName;   // may be null
    private final BigDecimal amount;
}
