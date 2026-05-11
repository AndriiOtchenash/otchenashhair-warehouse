package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.ReportService;
import com.hairmony.warehouse.web.dto.ReportPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

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

        model.addAttribute("period", period);
        model.addAttribute("expiryDays", expiryDays);
        model.addAttribute("expiringItems", reportService.getExpiringItems(expiryDays));
        model.addAttribute("reportSummary", reportService.getReportSummary(period.getFrom(), period.getTo()));
        model.addAttribute("stockValue", reportService.getStockValue());
        model.addAttribute("topSales", reportService.getTopSales(period.getFrom(), period.getTo()));
        model.addAttribute("writeOffsSummary", reportService.getWriteOffsSummary(period.getFrom(), period.getTo()));
        model.addAttribute("topClients", reportService.getTopClients(period.getFrom(), period.getTo()));
        model.addAttribute("purchasesSummary", reportService.getPurchasesSummary(period.getFrom(), period.getTo()));
        model.addAttribute("marginAnalysis", reportService.getMarginAnalysis(period.getFrom(), period.getTo()));
        model.addAttribute("slowMovers", reportService.getSlowMovers(period.getFrom(), period.getTo()));

        return "reports";
    }

    @GetMapping("/trends")
    public String trends(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        ReportPeriod period = resolvePeriod(preset, from, to);
        java.util.Map<String, Object> trends = reportService.getTrends(period.getFrom(), period.getTo());

        model.addAttribute("period", period);
        model.addAttribute("trendLabels", trends.get("labels"));
        model.addAttribute("trendRevenue", trends.get("revenue"));
        model.addAttribute("trendPurchases", trends.get("purchases"));
        model.addAttribute("trendSalesCount", trends.get("salesCount"));

        return "trends";
    }

    private ReportPeriod resolvePeriod(String preset, LocalDate from, LocalDate to) {
        if ("LAST_MONTH".equals(preset)) {
            LocalDate firstOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            LocalDate lastOfLastMonth = firstOfLastMonth.withDayOfMonth(firstOfLastMonth.lengthOfMonth());
            return new ReportPeriod(firstOfLastMonth, lastOfLastMonth, "LAST_MONTH");
        } else if ("ALL_TIME".equals(preset)) {
            LocalDate earliest = reportService.getEarliestMovementDate();
            return new ReportPeriod(earliest, LocalDate.now(), "ALL_TIME");
        } else if ("CUSTOM".equals(preset) && from != null && to != null) {
            return new ReportPeriod(from, to, "CUSTOM");
        } else {
            return ReportPeriod.thisMonth();
        }
    }
}
