package com.hairmony.warehouse.clientcare.web.dto;

public record ClientCareDashboardDto(
        long totalClients,
        long totalClientsInQueue,
        long needsContactCount,
        long longAbsentCount,
        long vipInactiveCount,
        long recentCount,
        long repeatPossibleCount,
        long overdueVisitCount,
        long upcomingVisitCount,
        long unpaidVisitCount,
        long noShowCount
) {}
