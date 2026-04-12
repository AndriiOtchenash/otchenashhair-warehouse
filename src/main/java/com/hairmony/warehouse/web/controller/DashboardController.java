package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.CategoryService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.StockDashboardRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final StockService stockService;
    private final CategoryService categoryService;

    @GetMapping("/")
    public String dashboard(Model model) {
        var rows = stockService.getDashboard();
        model.addAttribute("rows", rows);
        model.addAttribute("totalProducts", rows.size());
        model.addAttribute("lowStock", rows.stream()
                .filter(r -> r.getStatus() != StockDashboardRowDto.StockStatus.OK)
                .count());
        model.addAttribute("categories", categoryService.findAll()
                .stream()
                .map(c -> c.getName())
                .toList());
        return "dashboard";
    }
}
