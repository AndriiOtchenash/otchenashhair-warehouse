package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.ClientCareDashboardDto;
import com.hairmony.warehouse.clientcare.web.dto.ClientFollowupDto;
import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.StockMovementRepository;
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
    private final AppointmentRepository appointmentRepository;

    private static final List<AppointmentStatus> UPCOMING_STATUSES =
            List.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED);
    /** For follow-up queue: NO_SHOW counts as overdue signal (client needs contact). */
    private static final List<AppointmentStatus> FOLLOWUP_OVERDUE_STATUSES =
            List.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW);
    /** For KPI dashboard cards: only unresolved appointments (NO_SHOW already handled by user). */
    private static final List<AppointmentStatus> KPI_OVERDUE_STATUSES =
            List.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED);

    @Transactional(readOnly = true)
    public List<ClientFollowupDto> getFollowupQueue() {
        LocalDateTime now   = LocalDateTime.now();
        LocalDate today     = now.toLocalDate();

        // 1. Purchase-based signals
        Map<Long, ClientFollowupDto> byClientId = new LinkedHashMap<>();
        movementRepository.findClientSaleStats().stream()
                .map(row -> toPurchaseDto(row, now))
                .forEach(dto -> byClientId.put(dto.clientId(), dto));

        // 2. Appointment-based signals
        //    Upcoming: earliest PLANNED/CONFIRMED appointment from now onward per client
        //    Overdue:  most recent past PLANNED/CONFIRMED/NO_SHOW appointment per client (if no upcoming)
        Map<Long, LocalDate> apptSignalDate  = new LinkedHashMap<>();
        Map<Long, Long>      apptSignalDays  = new LinkedHashMap<>(); // negative = overdue
        Map<Long, Client>    apptClient      = new LinkedHashMap<>();

        // Upcoming — ordered ASC, putIfAbsent gives earliest per client
        List<Appointment> upcoming = appointmentRepository.findUpcomingForClients(now, UPCOMING_STATUSES);
        for (Appointment a : upcoming) {
            Long clientId = a.getClient().getId();
            if (!apptSignalDate.containsKey(clientId)) {
                LocalDate d = a.getStartAt().toLocalDate();
                apptSignalDate.put(clientId, d);
                apptSignalDays.put(clientId, ChronoUnit.DAYS.between(today, d));
                apptClient.put(clientId, a.getClient());
            }
        }

        // Overdue — only for clients without upcoming signal; ordered DESC (most recent first)
        List<Appointment> overdue = appointmentRepository.findOverdueForClients(now, FOLLOWUP_OVERDUE_STATUSES);
        for (Appointment a : overdue) {
            Long clientId = a.getClient().getId();
            if (!apptSignalDate.containsKey(clientId)) {
                LocalDate d = a.getStartAt().toLocalDate();
                apptSignalDate.put(clientId, d);
                apptSignalDays.put(clientId, ChronoUnit.DAYS.between(today, d)); // negative
                apptClient.put(clientId, a.getClient());
            }
        }

        // Merge appointment signals into the map
        for (Map.Entry<Long, LocalDate> entry : apptSignalDate.entrySet()) {
            Long clientId   = entry.getKey();
            LocalDate apptDate = entry.getValue();
            long daysUntil  = apptSignalDays.get(clientId);
            Client c        = apptClient.get(clientId);

            ClientFollowupDto existing = byClientId.get(clientId);
            if (existing != null) {
                byClientId.put(clientId, new ClientFollowupDto(
                        existing.clientId(), existing.clientName(), existing.clientPhone(),
                        existing.lastPurchaseAt(), existing.daysSinceLastPurchase(), existing.totalSpent(),
                        apptDate, daysUntil
                ));
            } else {
                // Appointment-only client (no purchases yet)
                byClientId.put(clientId, new ClientFollowupDto(
                        clientId, c.getName(), c.getPhone(),
                        null, 0, BigDecimal.ZERO,
                        apptDate, daysUntil
                ));
            }
        }

        // 3. Sort: overdue → today → upcoming → purchase-only (by days desc)
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
        LocalDateTime now        = LocalDateTime.now();

        List<ClientFollowupDto> all = movementRepository.findClientSaleStats().stream()
                .map(row -> toPurchaseDto(row, now))
                .toList();

        long totalClients = clientRepository.count();

        // Appointment-based KPI counts (distinct clients with a signal)
        Set<Long> upcomingClientIds = appointmentRepository
                .findUpcomingForClients(now, UPCOMING_STATUSES)
                .stream()
                .filter(a -> a.getClient() != null)
                .map(a -> a.getClient().getId())
                .collect(Collectors.toSet());
        Set<Long> overdueClientIds = appointmentRepository
                .findOverdueForClients(now, KPI_OVERDUE_STATUSES)
                .stream()
                .filter(a -> a.getClient() != null)
                .map(a -> a.getClient().getId())
                .filter(id -> !upcomingClientIds.contains(id)) // upcoming supersedes overdue
                .collect(Collectors.toSet());
        long upcomingVisit = upcomingClientIds.size();
        long overdueVisit  = overdueClientIds.size();

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
