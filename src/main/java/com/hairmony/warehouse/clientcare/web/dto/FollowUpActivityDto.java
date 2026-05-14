package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.followup.FollowUpAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FollowUpActivityDto(
        Long id,
        FollowUpAction action,
        LocalDate dueDate,
        String note,
        LocalDateTime createdAt
) {}
