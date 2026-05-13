package com.hairmony.warehouse.clientcare.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClientFollowupDto(
        Long clientId,
        String clientName,
        String clientPhone,
        LocalDateTime lastPurchaseAt,
        long daysSinceLastPurchase,
        BigDecimal totalSpent
) {
    public boolean hasPhone() {
        return clientPhone != null && !clientPhone.isBlank();
    }
}
