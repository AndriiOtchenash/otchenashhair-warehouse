package com.hairmony.warehouse.clientcare.web.dto;

import java.time.LocalDate;

public record UpcomingSnoozeDto(
        Long clientId,
        String clientName,
        String note,
        LocalDate dueDate,
        long daysUntil
) {}
