package com.hairmony.warehouse.clientcare.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CalendarTaskDto(
        Long id,
        LocalDate taskDate,
        Long clientId,
        String clientName,
        Long appointmentId,
        String text,
        boolean done,
        LocalDateTime createdAt
) {}
