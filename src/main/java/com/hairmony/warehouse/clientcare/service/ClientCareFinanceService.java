package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.*;
import com.hairmony.warehouse.domain.appointment.PaymentMethod;
import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
import com.hairmony.warehouse.domain.visit.Visit;
import com.hairmony.warehouse.repository.GiftCertificateRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientCareFinanceService {

    /** Payment methods that represent actual cash revenue. */
    private static final Set<PaymentMethod> REVENUE_METHODS =
            Set.of(PaymentMethod.CASH, PaymentMethod.CARD);

    private final VisitRepository visitRepository;
    private final SalonServiceService salonServiceService;
    private final GiftCertificateRepository certRepository;

    public LocalDate getEarliestVisitDate() {
        return visitRepository.findEarliestVisitDate()
                .orElse(LocalDate.now().withDayOfMonth(1));
    }

    public Map<String, Object> buildReport(LocalDate from, LocalDate to) {
        List<Visit> all = visitRepository.findWithClientByPeriod(from, to);

        // Service name lookup (all, including inactive — visits may reference old services)
        Map<Long, String> serviceNames = salonServiceService.findAll().stream()
                .collect(Collectors.toMap(SalonServiceDto::getId, SalonServiceDto::getName));

        // Paid visits with real money (CASH/CARD) — cert/barter/complimentary/promo excluded
        List<Visit> paidVisits = all.stream()
                .filter(v -> v.isPaid()
                        && v.getPriceAtTime() != null
                        && v.getPriceAtTime().compareTo(BigDecimal.ZERO) > 0
                        && REVENUE_METHODS.contains(v.getPaymentMethod()))
                .toList();

        // Unpaid visits with a positive price (real debt)
        List<Visit> unpaid = all.stream()
                .filter(v -> !v.isPaid()
                        && v.getPriceAtTime() != null
                        && v.getPriceAtTime().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        // Certificate sales in the period (excluding CANCELLED)
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();
        List<GiftCertificate> certSales = certRepository.findSoldInPeriod(fromDt, toDt);
        List<GiftCertificate> paidCerts = certSales.stream()
                .filter(c -> c.getPrice() != null && c.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal visitRevenue = sum(paidVisits);
        BigDecimal certRevenue  = paidCerts.stream()
                .map(GiftCertificate::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenue = visitRevenue.add(certRevenue);

        BigDecimal unpaidAmt  = sum(unpaid);
        int        visitCount = all.size();

        int revenueUnits = paidVisits.size() + paidCerts.size();
        BigDecimal avgTicket = revenueUnits == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(revenueUnits), 2, RoundingMode.HALF_UP);

        // Active (unredeemed) cert liability — current snapshot, not period-filtered
        BigDecimal certLiability = certRepository.sumActiveCertPrice();
        long certActiveCount     = certRepository.countByStatus(GiftCertificateStatus.ACTIVE);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenue",         revenue);
        result.put("visitCount",      visitCount);
        result.put("avgTicket",       avgTicket);
        result.put("unpaidAmount",    unpaidAmt);
        result.put("unpaidCount",     unpaid.size());
        result.put("certSoldCount",   paidCerts.size());
        result.put("certSoldRevenue", certRevenue);
        result.put("certActiveCount", certActiveCount);
        result.put("certLiability",   certLiability);
        result.put("byService",          buildByService(paidVisits, visitRevenue, serviceNames));
        result.put("byPaymentMethod",    buildByMethod(paidVisits, visitRevenue));
        result.put("topClients",         buildTopClients(paidVisits));
        result.put("unpaidVisits",       buildUnpaidList(unpaid, serviceNames));
        return result;
    }

    // Monthly trend: revenue (visits + certs) + visit count, all months in range pre-filled with zeros
    public Map<String, Object> buildTrends(LocalDate from, LocalDate to) {
        List<Visit> paidVisits = visitRepository.findWithClientByPeriod(from, to).stream()
                .filter(v -> v.isPaid()
                        && v.getPriceAtTime() != null
                        && v.getPriceAtTime().compareTo(BigDecimal.ZERO) > 0
                        && REVENUE_METHODS.contains(v.getPaymentMethod()))
                .toList();

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.plusDays(1).atStartOfDay();
        List<GiftCertificate> paidCerts = certRepository.findSoldInPeriod(fromDt, toDt).stream()
                .filter(c -> c.getPrice() != null && c.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

        TreeMap<String, BigDecimal[]> byMonth = new TreeMap<>();
        LocalDate cursor = from.withDayOfMonth(1);
        while (!cursor.isAfter(to)) {
            byMonth.put(cursor.format(fmt), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            cursor = cursor.plusMonths(1);
        }

        for (Visit v : paidVisits) {
            String month = v.getVisitDate().format(fmt);
            byMonth.computeIfAbsent(month, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            byMonth.get(month)[0] = byMonth.get(month)[0].add(v.getPriceAtTime());
            byMonth.get(month)[1] = byMonth.get(month)[1].add(BigDecimal.ONE);
        }

        for (GiftCertificate c : paidCerts) {
            String month = c.getIssuedAt().toLocalDate().format(fmt);
            byMonth.computeIfAbsent(month, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            byMonth.get(month)[0] = byMonth.get(month)[0].add(c.getPrice());
        }

        List<String> labels = new ArrayList<>(byMonth.keySet());
        List<BigDecimal> revenue = labels.stream()
                .map(k -> byMonth.get(k)[0].setScale(2, RoundingMode.HALF_UP)).toList();
        List<Integer> visitCount = labels.stream()
                .map(k -> byMonth.get(k)[1].intValue()).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("revenue", revenue);
        result.put("visitCount", visitCount);
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BigDecimal sum(List<Visit> visits) {
        return visits.stream()
                .map(Visit::getPriceAtTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sharePct(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private List<ServiceRevenueDto> buildByService(List<Visit> paid, BigDecimal totalRevenue,
                                                    Map<Long, String> serviceNames) {
        Map<Long, List<Visit>> grouped = new LinkedHashMap<>();
        for (Visit v : paid) {
            grouped.computeIfAbsent(v.getServiceId(), k -> new ArrayList<>()).add(v);
        }

        return grouped.entrySet().stream()
                .map(e -> {
                    BigDecimal rev = sum(e.getValue());
                    String name = e.getKey() == null ? null
                            : serviceNames.getOrDefault(e.getKey(), "#" + e.getKey());
                    return new ServiceRevenueDto(name, e.getValue().size(), rev, sharePct(rev, totalRevenue));
                })
                .sorted(Comparator.comparing(ServiceRevenueDto::getRevenue).reversed())
                .toList();
    }

    private List<PaymentBreakdownDto> buildByMethod(List<Visit> paid, BigDecimal totalRevenue) {
        Map<PaymentMethod, List<Visit>> grouped = paid.stream()
                .filter(v -> v.getPaymentMethod() != null)
                .collect(Collectors.groupingBy(
                        Visit::getPaymentMethod,
                        () -> new EnumMap<>(PaymentMethod.class),
                        Collectors.toList()));

        return Arrays.stream(PaymentMethod.values())
                .filter(REVENUE_METHODS::contains) // only cash revenue methods
                .filter(grouped::containsKey)
                .map(pm -> {
                    List<Visit> group = grouped.get(pm);
                    BigDecimal rev = sum(group);
                    return new PaymentBreakdownDto(pm, group.size(), rev, sharePct(rev, totalRevenue));
                })
                .sorted(Comparator.comparing(PaymentBreakdownDto::getRevenue).reversed())
                .toList();
    }

    private List<TopClientFinanceDto> buildTopClients(List<Visit> paid) {
        return paid.stream()
                .collect(Collectors.groupingBy(v -> v.getClient().getId()))
                .entrySet().stream()
                .map(e -> {
                    List<Visit> group = e.getValue();
                    String name = group.get(0).getClient().getName();
                    BigDecimal total = sum(group);
                    return new TopClientFinanceDto(e.getKey(), name, group.size(), total);
                })
                .sorted(Comparator.comparing(TopClientFinanceDto::getTotalSpent).reversed())
                .limit(7)
                .toList();
    }

    private List<UnpaidVisitRowDto> buildUnpaidList(List<Visit> unpaid, Map<Long, String> serviceNames) {
        return unpaid.stream()
                .map(v -> new UnpaidVisitRowDto(
                        v.getId(),
                        v.getClient().getId(),
                        v.getClient().getName(),
                        v.getVisitDate(),
                        v.getServiceId() != null ? serviceNames.get(v.getServiceId()) : null,
                        v.getPriceAtTime()))
                .toList();
    }
}
