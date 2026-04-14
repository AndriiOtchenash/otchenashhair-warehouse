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

        ReportPeriod period;
        if ("LAST_MONTH".equals(preset)) {
            LocalDate firstOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            LocalDate lastOfLastMonth = firstOfLastMonth.withDayOfMonth(
                    firstOfLastMonth.lengthOfMonth());
            period = new ReportPeriod(firstOfLastMonth, lastOfLastMonth, "LAST_MONTH");
        } else if ("CUSTOM".equals(preset) && from != null && to != null) {
            period = new ReportPeriod(from, to, "CUSTOM");
        } else {
            period = ReportPeriod.thisMonth();
        }

        model.addAttribute("period", period);
        model.addAttribute("expiryDays", expiryDays);
        model.addAttribute("expiringItems", reportService.getExpiringItems(expiryDays));
        model.addAttribute("topSales", reportService.getTopSales(period.getFrom(), period.getTo()));
        model.addAttribute("purchasesSummary", reportService.getPurchasesSummary(period.getFrom(), period.getTo()));
        model.addAttribute("marginAnalysis", reportService.getMarginAnalysis(period.getFrom(), period.getTo()));

        return "reports";
    }
}
