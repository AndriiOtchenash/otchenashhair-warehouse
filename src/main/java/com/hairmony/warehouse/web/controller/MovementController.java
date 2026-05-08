package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.service.MovementHistoryService;
import com.hairmony.warehouse.service.ProductService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.MovementFilterDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MovementController {

    private final MovementHistoryService movementHistoryService;
    private final ProductService productService;
    private final StockService stockService;
    private final ClientService clientService;

    @GetMapping("/movements/history")
    public String history(MovementFilterDto filter, Model model) {
        var page = movementHistoryService.findFiltered(filter);
        List<StockMovement> movements = page.getContent();

        Set<Long> pageIds = movements.stream()
                .map(StockMovement::getId)
                .collect(Collectors.toSet());
        Set<Long> cancelledIds = stockService.getCancelledMovementIds(pageIds);

        model.addAttribute("page", page);
        model.addAttribute("movements", movements);
        model.addAttribute("filter", filter);
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("movementTypes", MovementType.values());
        model.addAttribute("totalElements", page.getTotalElements());
        model.addAttribute("cancelledMovementIds", cancelledIds);
        model.addAttribute("clients", clientService.findAll());
        return "movements/history";
    }
}
