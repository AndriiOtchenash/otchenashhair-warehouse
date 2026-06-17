package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.ClientCareDashboardDto;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientCareServiceTest {

    @Mock StockMovementRepository movementRepository;
    @Mock ClientRepository clientRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock VisitService visitService;

    @InjectMocks ClientCareService clientCareService;

    // Stub appointment queries to empty (testing only purchase-based segments here)
    @BeforeEach
    void stubAppointments() {
        when(appointmentRepository.findUpcomingForClients(any(), any())).thenReturn(List.of());
        when(appointmentRepository.findOverdueForClients(any(), any())).thenReturn(List.of());
        when(visitService.getClientIdsWithUnpaidVisits()).thenReturn(Set.of());
        when(clientRepository.count()).thenReturn(10L);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Build a row as returned by findClientSaleStats(): [clientId, name, phone, lastPurchase, totalSpent] */
    private Object[] row(long id, String phone, long daysAgo, BigDecimal spent) {
        LocalDateTime lastPurchase = LocalDateTime.now().minusDays(daysAgo);
        return new Object[]{id, "Client " + id, phone, lastPurchase, spent};
    }

    private ClientCareDashboardDto dashboard(Object[]... rows) {
        when(movementRepository.findClientSaleStats()).thenReturn(List.of(rows));
        return clientCareService.getDashboardData();
    }

    // ── no clients ───────────────────────────────────────────────────────────

    @Test
    void noClients_allPurchaseCountsAreZero() {
        when(movementRepository.findClientSaleStats()).thenReturn(List.of());
        ClientCareDashboardDto dto = clientCareService.getDashboardData();

        assertThat(dto.totalClientsInQueue()).isZero();
        assertThat(dto.needsContactCount()).isZero();
        assertThat(dto.longAbsentCount()).isZero();
        assertThat(dto.recentCount()).isZero();
        assertThat(dto.repeatPossibleCount()).isZero();
        assertThat(dto.vipInactiveCount()).isZero();
    }

    // ── needsContact (>30 days + hasPhone) ───────────────────────────────────

    @Test
    void needsContact_countedWhen31DaysAndHasPhone() {
        ClientCareDashboardDto dto = dashboard(
                row(1, "+48111222333", 31, BigDecimal.TEN)
        );
        assertThat(dto.needsContactCount()).isEqualTo(1);
    }

    @Test
    void needsContact_notCountedWithoutPhone() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 31, BigDecimal.TEN)
        );
        assertThat(dto.needsContactCount()).isZero();
    }

    @Test
    void needsContact_notCountedAt30Days() {
        // boundary: > 30 days required, exactly 30 days should NOT count
        ClientCareDashboardDto dto = dashboard(
                row(1, "+48111222333", 30, BigDecimal.TEN)
        );
        assertThat(dto.needsContactCount()).isZero();
    }

    // ── longAbsent (>60 days) ────────────────────────────────────────────────

    @Test
    void longAbsent_countedWhen61Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, "+48111", 61, BigDecimal.TEN)
        );
        assertThat(dto.longAbsentCount()).isEqualTo(1);
    }

    @Test
    void longAbsent_notCountedAt60Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, "+48111", 60, BigDecimal.TEN)
        );
        assertThat(dto.longAbsentCount()).isZero();
    }

    // ── recent (<14 days) ────────────────────────────────────────────────────

    @Test
    void recent_countedWhen5Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 5, BigDecimal.TEN)
        );
        assertThat(dto.recentCount()).isEqualTo(1);
    }

    @Test
    void recent_notCountedAt14Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 14, BigDecimal.TEN)
        );
        assertThat(dto.recentCount()).isZero();
    }

    // ── repeatPossible (25–40 days) ──────────────────────────────────────────

    @Test
    void repeatPossible_countedAt30Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 30, BigDecimal.TEN)
        );
        assertThat(dto.repeatPossibleCount()).isEqualTo(1);
    }

    @Test
    void repeatPossible_countedAtBoundary25Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 25, BigDecimal.TEN)
        );
        assertThat(dto.repeatPossibleCount()).isEqualTo(1);
    }

    @Test
    void repeatPossible_notCountedAt41Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 41, BigDecimal.TEN)
        );
        assertThat(dto.repeatPossibleCount()).isZero();
    }

    @Test
    void repeatPossible_notCountedAt24Days() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 24, BigDecimal.TEN)
        );
        assertThat(dto.repeatPossibleCount()).isZero();
    }

    // ── vipInactive (top 20% by spend + >30 days) ────────────────────────────

    @Test
    void vipInactive_topSpenderWith31DaysIsVip() {
        // 5 clients: top 20% = ceil(5*0.2) = 1 → client with highest spend
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 31, new BigDecimal("1000")),  // top spender → VIP, >30d → counted
                row(2, null, 31, new BigDecimal("100")),
                row(3, null, 31, new BigDecimal("50")),
                row(4, null, 31, new BigDecimal("30")),
                row(5, null, 31, new BigDecimal("10"))
        );
        assertThat(dto.vipInactiveCount()).isEqualTo(1);
    }

    @Test
    void vipInactive_topSpenderUnder30DaysNotCounted() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 20, new BigDecimal("1000")), // VIP but only 20 days → not counted
                row(2, null, 31, new BigDecimal("100")),
                row(3, null, 31, new BigDecimal("50")),
                row(4, null, 31, new BigDecimal("30")),
                row(5, null, 31, new BigDecimal("10"))
        );
        assertThat(dto.vipInactiveCount()).isZero();
    }

    @Test
    void vipPercentile_singleClientIsAlwaysVip() {
        // Math.max(1, ceil(1 * 0.2)) = Math.max(1, 1) = 1 → single client is always VIP
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 31, BigDecimal.TEN)
        );
        assertThat(dto.vipInactiveCount()).isEqualTo(1);
    }

    @Test
    void totalClientsInQueue_equalsRowCount() {
        ClientCareDashboardDto dto = dashboard(
                row(1, null, 5, BigDecimal.TEN),
                row(2, null, 10, BigDecimal.TEN),
                row(3, null, 50, BigDecimal.TEN)
        );
        assertThat(dto.totalClientsInQueue()).isEqualTo(3);
    }
}
