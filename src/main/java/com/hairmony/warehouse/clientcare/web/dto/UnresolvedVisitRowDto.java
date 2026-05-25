package com.hairmony.warehouse.clientcare.web.dto;

import java.time.LocalDate;

public record UnresolvedVisitRowDto(
        Long visitId,
        Long clientId,
        String clientName,
        String clientPhone,
        LocalDate lastVisitDate,
        long daysSinceLastVisit
) {
    public boolean hasPhone() {
        return clientPhone != null && !clientPhone.isBlank();
    }
}
