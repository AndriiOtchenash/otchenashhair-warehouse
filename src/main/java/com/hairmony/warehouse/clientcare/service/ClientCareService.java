package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.ClientCareDashboardDto;
import com.hairmony.warehouse.clientcare.web.dto.ClientFollowupDto;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientCareService {

    private static final int NEEDS_CONTACT_DAYS  = 30;
    private static final int LONG_ABSENT_DAYS    = 60;
    private static final int RECENT_DAYS         = 14;
    private static final int REPEAT_FROM_DAYS    = 25;
    private static final int REPEAT_TO_DAYS      = 40;
    private static final double VIP_PERCENTILE   = 0.20;

    private final StockMovementRepository movementRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ClientFollowupDto> getFollowupQueue() {
        LocalDateTime now = LocalDateTime.now();
        return movementRepository.findClientSaleStats().stream()
                .map(row -> toDto(row, now))
                .sorted(Comparator.comparingLong(ClientFollowupDto::daysSinceLastPurchase).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientCareDashboardDto getDashboardData() {
        LocalDateTime now = LocalDateTime.now();
        List<ClientFollowupDto> all = movementRepository.findClientSaleStats().stream()
                .map(row -> toDto(row, now))
                .toList();

        long totalClients = clientRepository.count();

        if (all.isEmpty()) {
            return new ClientCareDashboardDto(totalClients, 0, 0, 0, 0, 0, 0);
        }

        List<ClientFollowupDto> sortedBySpent = all.stream()
                .sorted(Comparator.comparing(ClientFollowupDto::totalSpent).reversed())
                .toList();
        int vipCount = Math.max(1, (int) Math.ceil(sortedBySpent.size() * VIP_PERCENTILE));
        Set<Long> vipIds = sortedBySpent.subList(0, vipCount).stream()
                .map(ClientFollowupDto::clientId)
                .collect(Collectors.toSet());

        long needsContact  = all.stream().filter(c -> c.daysSinceLastPurchase() > NEEDS_CONTACT_DAYS && c.hasPhone()).count();
        long longAbsent    = all.stream().filter(c -> c.daysSinceLastPurchase() > LONG_ABSENT_DAYS).count();
        long vipInactive   = all.stream().filter(c -> vipIds.contains(c.clientId()) && c.daysSinceLastPurchase() > NEEDS_CONTACT_DAYS).count();
        long recent        = all.stream().filter(c -> c.daysSinceLastPurchase() < RECENT_DAYS).count();
        long repeatPossible = all.stream().filter(c -> c.daysSinceLastPurchase() >= REPEAT_FROM_DAYS && c.daysSinceLastPurchase() <= REPEAT_TO_DAYS).count();

        return new ClientCareDashboardDto(totalClients, all.size(), needsContact, longAbsent, vipInactive, recent, repeatPossible);
    }

    private ClientFollowupDto toDto(Object[] row, LocalDateTime now) {
        Long clientId        = (Long) row[0];
        String name          = (String) row[1];
        String phone         = (String) row[2];
        LocalDateTime lastPurchase = (LocalDateTime) row[3];
        BigDecimal totalSpent = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
        long days = ChronoUnit.DAYS.between(lastPurchase, now);
        return new ClientFollowupDto(clientId, name, phone, lastPurchase, days, totalSpent);
    }
}
