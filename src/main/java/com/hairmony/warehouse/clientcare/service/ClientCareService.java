package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.ClientCareDashboardDto;
import com.hairmony.warehouse.clientcare.web.dto.ClientFollowupDto;
import com.hairmony.warehouse.domain.visit.Visit;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.StockMovementRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientCareService {

    private static final int NEEDS_CONTACT_DAYS = 30;
    private static final int LONG_ABSENT_DAYS   = 60;
    private static final int RECENT_DAYS        = 14;
    private static final int REPEAT_FROM_DAYS   = 25;
    private static final int REPEAT_TO_DAYS     = 40;
    private static final double VIP_PERCENTILE  = 0.20;

    private final StockMovementRepository movementRepository;
    private final ClientRepository clientRepository;
    private final VisitRepository visitRepository;

    @Transactional(readOnly = true)
    public List<ClientFollowupDto> getFollowupQueue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today   = now.toLocalDate();

        // 1. Purchase-based signals
        Map<Long, ClientFollowupDto> byClientId = new LinkedHashMap<>();
        movementRepository.findClientSaleStats().stream()
                .map(row -> toPurchaseDto(row, now))
                .forEach(dto -> byClientId.put(dto.clientId(), dto));

        // 2. Visit-based signals — all clients with any nextVisitDate set
        List<Visit> visitSignals = visitRepository.findAllLatestWithNextVisitDate();

        for (Visit v : visitSignals) {
            Long clientId = v.getClient().getId();
            LocalDate nextVisit = v.getNextVisitDate();
            long daysUntil = ChronoUnit.DAYS.between(today, nextVisit);

            ClientFollowupDto existing = byClientId.get(clientId);
            if (existing != null) {
                // Merge visit signal into existing purchase entry
                byClientId.put(clientId, new ClientFollowupDto(
                        existing.clientId(),
                        existing.clientName(),
                        existing.clientPhone(),
                        existing.lastPurchaseAt(),
                        existing.daysSinceLastPurchase(),
                        existing.totalSpent(),
                        nextVisit,
                        daysUntil
                ));
            } else {
                // Visit-only client (no purchases yet)
                byClientId.put(clientId, new ClientFollowupDto(
                        clientId,
                        v.getClient().getName(),
                        v.getClient().getPhone(),
                        null,
                        0,
                        BigDecimal.ZERO,
                        nextVisit,
                        daysUntil
                ));
            }
        }

        // 3. Sort: overdue visit → today → upcoming visit → purchase-only (by days desc)
        return byClientId.values().stream()
                .sorted(Comparator
                        .comparingInt(ClientFollowupDto::signalPriority)
                        .thenComparingLong((ClientFollowupDto c) -> {
                            if (c.hasVisitSignal()) return c.daysUntilNextVisit(); // overdue first = most negative
                            return -c.daysSinceLastPurchase(); // purchase: most days = most urgent
                        }))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClientCareDashboardDto getDashboardData() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today   = now.toLocalDate();

        List<ClientFollowupDto> all = movementRepository.findClientSaleStats().stream()
                .map(row -> toPurchaseDto(row, now))
                .toList();

        long totalClients = clientRepository.count();

        // Visit signal counts (independent of purchase signals)
        List<Visit> visits = visitRepository.findAllLatestWithNextVisitDate();
        long overdueVisit  = visits.stream().filter(v -> v.getNextVisitDate().isBefore(today)).count();
        long upcomingVisit = visits.stream().filter(v -> !v.getNextVisitDate().isBefore(today)).count();

        if (all.isEmpty()) {
            return new ClientCareDashboardDto(totalClients, 0, 0, 0, 0, 0, 0, overdueVisit, upcomingVisit);
        }

        List<ClientFollowupDto> sortedBySpent = all.stream()
                .sorted(Comparator.comparing(ClientFollowupDto::totalSpent).reversed())
                .toList();
        int vipCount = Math.max(1, (int) Math.ceil(sortedBySpent.size() * VIP_PERCENTILE));
        Set<Long> vipIds = sortedBySpent.subList(0, vipCount).stream()
                .map(ClientFollowupDto::clientId)
                .collect(Collectors.toSet());

        long needsContact   = all.stream().filter(c -> c.daysSinceLastPurchase() > NEEDS_CONTACT_DAYS && c.hasPhone()).count();
        long longAbsent     = all.stream().filter(c -> c.daysSinceLastPurchase() > LONG_ABSENT_DAYS).count();
        long vipInactive    = all.stream().filter(c -> vipIds.contains(c.clientId()) && c.daysSinceLastPurchase() > NEEDS_CONTACT_DAYS).count();
        long recent         = all.stream().filter(c -> c.daysSinceLastPurchase() < RECENT_DAYS).count();
        long repeatPossible = all.stream().filter(c -> c.daysSinceLastPurchase() >= REPEAT_FROM_DAYS && c.daysSinceLastPurchase() <= REPEAT_TO_DAYS).count();

        return new ClientCareDashboardDto(totalClients, all.size(), needsContact, longAbsent, vipInactive, recent, repeatPossible, overdueVisit, upcomingVisit);
    }

    private ClientFollowupDto toPurchaseDto(Object[] row, LocalDateTime now) {
        Long clientId          = (Long) row[0];
        String name            = (String) row[1];
        String phone           = (String) row[2];
        LocalDateTime lastPurchase = (LocalDateTime) row[3];
        BigDecimal totalSpent  = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
        long days = ChronoUnit.DAYS.between(lastPurchase, now);
        return new ClientFollowupDto(clientId, name, phone, lastPurchase, days, totalSpent, null, 0);
    }
}
