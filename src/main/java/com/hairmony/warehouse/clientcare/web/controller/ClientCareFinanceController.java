package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.ClientCareFinanceService;
import com.hairmony.warehouse.web.dto.ReportPeriod;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Controller
@RequestMapping("/clientcare/finance")
@RequiredArgsConstructor
public class ClientCareFinanceController {

    private final ClientCareFinanceService financeService;

    @GetMapping
    public String finance(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request,
            Model model) {

        ReportPeriod period = resolvePeriod(preset, from, to);
        Map<String, Object> report = financeService.buildReport(period.getFrom(), period.getTo());

        String qs = request.getQueryString();
        String currentPageUrl = "/clientcare/finance" + (qs != null ? "?" + qs : "");

        Map<String, Object> trends = financeService.buildTrends(period.getFrom(), period.getTo());

        model.addAttribute("period", period);
        model.addAttribute("report", report);
        model.addAttribute("currentPageUrl", currentPageUrl);
        model.addAttribute("trendLabels", trends.get("labels"));
        model.addAttribute("trendRevenue", trends.get("revenue"));
        model.addAttribute("trendVisitCount", trends.get("visitCount"));

        // Previous-period revenue delta
        ReportPeriod prev = previousPeriod(period);
        if (prev != null) {
            Map<String, Object> prevReport = financeService.buildReport(prev.getFrom(), prev.getTo());
            model.addAttribute("revenueDelta",
                    delta((BigDecimal) report.get("revenue"), (BigDecimal) prevReport.get("revenue")));
            model.addAttribute("visitCountDelta",
                    deltaInt((int) report.get("visitCount"), (int) prevReport.get("visitCount")));
            model.addAttribute("avgTicketDelta",
                    delta((BigDecimal) report.get("avgTicket"), (BigDecimal) prevReport.get("avgTicket")));
        }

        return "clientcare/finance";
    }

    // ── Period helpers (same pattern as ReportController) ────────────────────

    private ReportPeriod resolvePeriod(String preset, LocalDate from, LocalDate to) {
        if ("LAST_MONTH".equals(preset)) {
            LocalDate first = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            return new ReportPeriod(first, first.withDayOfMonth(first.lengthOfMonth()), "LAST_MONTH");
        } else if ("ALL_TIME".equals(preset)) {
            LocalDate earliest = financeService.getEarliestVisitDate();
            return new ReportPeriod(earliest, LocalDate.now(), "ALL_TIME");
        } else if ("CUSTOM".equals(preset) && from != null && to != null) {
            if (to.isBefore(from)) { LocalDate tmp = from; from = to; to = tmp; }
            return new ReportPeriod(from, to, "CUSTOM");
        } else {
            return ReportPeriod.thisMonth();
        }
    }

    private ReportPeriod previousPeriod(ReportPeriod current) {
        if ("ALL_TIME".equals(current.getPreset())) return null;
        if ("THIS_MONTH".equals(current.getPreset()) || "LAST_MONTH".equals(current.getPreset())) {
            LocalDate first = current.getFrom().minusMonths(1).withDayOfMonth(1);
            return new ReportPeriod(first, first.withDayOfMonth(first.lengthOfMonth()), null);
        }
        long days = ChronoUnit.DAYS.between(current.getFrom(), current.getTo());
        return new ReportPeriod(current.getFrom().minusDays(days + 1), current.getFrom().minusDays(1), null);
    }

    private BigDecimal delta(BigDecimal current, BigDecimal previous) {
        if (previous == null) return null;
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : null;
        }
        return compact(current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP));
    }

    private BigDecimal deltaInt(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? BigDecimal.valueOf(100) : null;
        }
        return compact(BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP));
    }

    private BigDecimal compact(BigDecimal v) {
        return v.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
                ? v.setScale(0) : v;
    }
}
