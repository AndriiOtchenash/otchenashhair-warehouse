package com.hairmony.warehouse.clientcare.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClientFollowupDto(
        Long clientId,
        String clientName,
        String clientPhone,
        // Purchase signal
        LocalDateTime lastPurchaseAt,
        long daysSinceLastPurchase,
        BigDecimal totalSpent,
        // Visit signal
        LocalDate nextVisitDate,
        long daysUntilNextVisit,  // negative = overdue, 0 = today, positive = upcoming
        boolean visitPastToday,   // true when appointment was today but already started (shows as overdue)
        boolean visitIsNoShow     // true when the overdue signal comes from a NO_SHOW appointment
) {
    public boolean hasPhone() {
        return clientPhone != null && !clientPhone.isBlank();
    }

    public boolean hasPurchaseSignal() {
        return lastPurchaseAt != null;
    }

    public boolean hasVisitSignal() {
        return nextVisitDate != null;
    }

    public boolean visitOverdue() {
        return nextVisitDate != null && daysUntilNextVisit < 0;
    }

    /** Appointment was today but has already started — shows "сьогодні" with overdue styling. */
    public boolean visitTodayOverdue() {
        return nextVisitDate != null && daysUntilNextVisit == 0 && visitPastToday;
    }

    public boolean visitToday() {
        return nextVisitDate != null && daysUntilNextVisit == 0 && !visitPastToday;
    }

    public boolean visitUpcoming() {
        return nextVisitDate != null && daysUntilNextVisit > 0;
    }

    /** Priority for sorting: lower = more urgent */
    public int signalPriority() {
        if (visitOverdue() || visitTodayOverdue()) return 0;
        if (visitToday())    return 1;
        if (visitUpcoming()) return 2;
        return 3;
    }
}
