package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.followup.FollowUpAction;

import java.time.LocalDate;

public record LatestFollowUpDto(
        Long clientId,
        FollowUpAction action,
        LocalDate dueDate,
        String note
) {
    public boolean isActiveSnoozed() {
        return action == FollowUpAction.SNOOZE
                && dueDate != null
                && dueDate.isAfter(LocalDate.now());
    }

    /** DONE with future dueDate = auto-hidden for 7 days (Variant A) */
    public boolean isRecentlyDone() {
        return action == FollowUpAction.DONE
                && dueDate != null
                && dueDate.isAfter(LocalDate.now());
    }
}
