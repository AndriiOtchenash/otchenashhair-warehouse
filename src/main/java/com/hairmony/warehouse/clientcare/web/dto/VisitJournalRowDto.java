package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.appointment.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class VisitJournalRowDto {
    private Long id;
    private LocalDate visitDate;
    private Long clientId;
    private String clientName;
    private Long serviceId;
    private BigDecimal priceAtTime;
    private PaymentMethod paymentMethod;
    private boolean paid;
}
