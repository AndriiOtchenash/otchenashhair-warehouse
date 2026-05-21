package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.appointment.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class PaymentBreakdownDto {
    private final PaymentMethod method;   // null means "not specified"
    private final int visitCount;
    private final BigDecimal revenue;
    private final BigDecimal sharePct;
}
