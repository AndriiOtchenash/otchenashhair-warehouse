package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.CategoryService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.StockDashboardRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final StockService stockService;
    private final CategoryService categoryService;

    @GetMapping("/")
    public String dashboard(@RequestParam(defaultValue = "OK") String status, Model model) {
        var rows = stockService.getDashboard();
        model.addAttribute("rows", rows);
        model.addAttribute("totalProducts", rows.size());
        model.addAttribute("countOk", rows.stream()
                .filter(r -> r.getStatus() == StockDashboardRowDto.StockStatus.OK
                          || r.getStatus() == StockDashboardRowDto.StockStatus.LOW)
                .count());
        model.addAttribute("lowStock", rows.stream()
                .filter(r -> r.getStatus() != StockDashboardRowDto.StockStatus.OK)
                .count());
        model.addAttribute("activeStatus", status);
        model.addAttribute("categories", categoryService.findAll()
                .stream()
                .map(c -> c.getName())
                .toList());
        model.addAttribute("brands", stockService.getDistinctBrands());
        return "dashboard";
    }
}
