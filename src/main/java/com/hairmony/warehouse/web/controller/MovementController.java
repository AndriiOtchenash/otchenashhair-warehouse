package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.WriteOffReason;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.service.MovementHistoryService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.MovementFilterDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.data.domain.Page;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class MovementController {

    private final MovementHistoryService movementHistoryService;
    private final StockService stockService;
    private final ClientService clientService;

    @GetMapping("/movements/history")
    public String history(MovementFilterDto filter, Model model) {
        Page<StockMovement> page = movementHistoryService.findFiltered(filter);

        Set<Long> ids = page.getContent().stream()
                .map(StockMovement::getId)
                .collect(Collectors.toSet());
        Set<Long> cancelledIds = stockService.getCancelledMovementIds(ids);

        model.addAttribute("movements", page.getContent());
        model.addAttribute("filter", filter);
        model.addAttribute("movementTypes", MovementType.values());
        model.addAttribute("writeOffReasons", WriteOffReason.values());
        model.addAttribute("totalElements", page.getTotalElements());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("cancelledMovementIds", cancelledIds);
        model.addAttribute("clients", clientService.findAll());
        return "movements/history";
    }
}
