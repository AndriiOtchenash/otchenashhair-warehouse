package com.hairmony.warehouse.clientcare.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

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

    private String complaint;       // Скарга клієнта
    private String scalpCondition;  // Стан шкіри голови при огляді
    private String recommendations; // Рекомендації (засоби, процедури)

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate nextVisitDate;

    private String notes;           // Внутрішні примітки

    private LocalDateTime createdAt;
}
