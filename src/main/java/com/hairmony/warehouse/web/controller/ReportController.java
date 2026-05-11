package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.ReportService;
import com.hairmony.warehouse.web.dto.ReportPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public String reports(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "30") int expiryDays,
            Model model) {

        ReportPeriod period = resolvePeriod(preset, from, to);
        Map<String, Object> summary = reportService.getReportSummary(period.getFrom(), period.getTo());

        model.addAttribute("period", period);
        model.addAttribute("expiryDays", expiryDays);
        model.addAttribute("expiringItems", reportService.getExpiringItems(expiryDays));
        model.addAttribute("reportSummary", summary);
        model.addAttribute("stockValue", reportService.getStockValue());
        model.addAttribute("topSales", reportService.getTopSales(period.getFrom(), period.getTo()));
        model.addAttribute("writeOffsSummary", reportService.getWriteOffsSummary(period.getFrom(), period.getTo()));
        model.addAttribute("topClients", reportService.getTopClients(period.getFrom(), period.getTo()));
        model.addAttribute("purchasesSummary", reportService.getPurchasesSummary(period.getFrom(), period.getTo()));
        model.addAttribute("marginAnalysis", reportService.getMarginAnalysis(period.getFrom(), period.getTo()));
        model.addAttribute("slowMovers", reportService.getSlowMovers(period.getFrom(), period.getTo()));

        // Previous-period comparison (not shown for ALL_TIME)
        ReportPeriod prev = previousPeriod(period);
        if (prev != null) {
            Map<String, Object> prevSummary = reportService.getReportSummary(prev.getFrom(), prev.getTo());
            model.addAttribute("revenueDelta",   delta((BigDecimal) summary.get("totalRevenue"),   (BigDecimal) prevSummary.get("totalRevenue")));
            model.addAttribute("purchasesDelta", delta((BigDecimal) summary.get("totalPurchases"), (BigDecimal) prevSummary.get("totalPurchases")));
            model.addAttribute("salesCountDelta", deltaInt((int) summary.get("salesCount"),        (int) prevSummary.get("salesCount")));
            model.addAttribute("profitDelta",    delta((BigDecimal) summary.get("grossProfit"),    (BigDecimal) prevSummary.get("grossProfit")));
        }

        return "reports";
    }

    @GetMapping("/trends")
    public String trends(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        ReportPeriod period = resolvePeriod(preset, from, to);
        Map<String, Object> trends = reportService.getTrends(period.getFrom(), period.getTo());

        model.addAttribute("period", period);
        model.addAttribute("trendLabels", trends.get("labels"));
        model.addAttribute("trendRevenue", trends.get("revenue"));
        model.addAttribute("trendPurchases", trends.get("purchases"));
        model.addAttribute("trendSalesCount", trends.get("salesCount"));

        return "trends";
    }

    private ReportPeriod resolvePeriod(String preset, LocalDate from, LocalDate to) {
        if ("LAST_MONTH".equals(preset)) {
            LocalDate first = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            return new ReportPeriod(first, first.withDayOfMonth(first.lengthOfMonth()), "LAST_MONTH");
        } else if ("ALL_TIME".equals(preset)) {
            return new ReportPeriod(reportService.getEarliestMovementDate(), LocalDate.now(), "ALL_TIME");
        } else if ("CUSTOM".equals(preset) && from != null && to != null) {
            return new ReportPeriod(from, to, "CUSTOM");
        } else {
            return ReportPeriod.thisMonth();
        }
    }

    // Returns previous period or null (for ALL_TIME)
    private ReportPeriod previousPeriod(ReportPeriod current) {
        if ("ALL_TIME".equals(current.getPreset())) return null;
        if ("THIS_MONTH".equals(current.getPreset()) || "LAST_MONTH".equals(current.getPreset())) {
            LocalDate first = current.getFrom().minusMonths(1).withDayOfMonth(1);
            return new ReportPeriod(first, first.withDayOfMonth(first.lengthOfMonth()), null);
        }
        // CUSTOM: same duration shifted back by 1 day
        long days = ChronoUnit.DAYS.between(current.getFrom(), current.getTo());
        return new ReportPeriod(current.getFrom().minusDays(days + 1), current.getFrom().minusDays(1), null);
    }

    // % change vs previous; null only when both are zero (nothing to show)
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

    // % change for integer counts
    private BigDecimal deltaInt(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? BigDecimal.valueOf(100) : null;
        }
        return compact(BigDecimal.valueOf(current - previous)
                .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP));
    }

    // Strip ".0" suffix: 12.3 stays 12.3, 100.0 becomes 100
    private BigDecimal compact(BigDecimal v) {
        return v.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
                ? v.setScale(0) : v;
    }
}
