package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.appointment.PaymentMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class VisitDto {

    private Long id;

    @NotNull
    private Long clientId;

    @NotNull
    @PastOrPresent
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate visitDate;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String complaint;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String scalpCondition;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String recommendations;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String notes;

    private LocalDateTime createdAt;

    // Populated from Visit.nextAppointment (read-only in form)
    private Long nextAppointmentId;
    private LocalDateTime nextAppointmentStartAt;

    // --- Financial fields (draft: backed by migration 021) ---

    private Long serviceId;

    @DecimalMin(value = "0.01", message = "{visit.price.min}")
    private BigDecimal priceAtTime;

    private PaymentMethod paymentMethod;

    private boolean paid;

    @AssertTrue(message = "{visit.paymentMethod.requiredWhenPaid}")
    public boolean isPaymentMethodRequiredWhenPaid() {
        return !paid || paymentMethod != null;
    }

    @Size(max = 20, message = "{validation.size.max20}")
    private String certificateCode;
}
