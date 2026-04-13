package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.service.MovementHistoryService;
import com.hairmony.warehouse.service.ProductService;
import com.hairmony.warehouse.web.dto.MovementFilterDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MovementController {

    private final MovementHistoryService movementHistoryService;
    private final ProductService productService;

    @GetMapping("/movements/history")
    public String history(MovementFilterDto filter, Model model) {
        var page = movementHistoryService.findFiltered(filter);
        model.addAttribute("page", page);
        model.addAttribute("movements", page.getContent());
        model.addAttribute("filter", filter);
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("movementTypes", MovementType.values());
        model.addAttribute("totalElements", page.getTotalElements());
        return "movements/history";
    }
}
