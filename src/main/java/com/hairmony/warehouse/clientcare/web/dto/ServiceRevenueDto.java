package com.hairmony.warehouse.clientcare.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ServiceRevenueDto {
    private final String serviceName;
    private final int visitCount;
    private final BigDecimal revenue;
    private final BigDecimal sharePct;
}
