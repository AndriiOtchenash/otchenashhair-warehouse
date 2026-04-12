package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;
    private final StockService stockService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("newClient", new ClientDto());
        return "clients/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("client", clientService.findById(id));
        model.addAttribute("movements", stockService.getMovementsByClient(id));
        return "clients/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("client", clientService.findById(id));
        return "clients/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newClient") ClientDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("clients", clientService.findAll());
            return "clients/list";
        }
        clientService.save(dto);
        return "redirect:/clients";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("client") ClientDto dto,
                         BindingResult result) {
        if (result.hasErrors()) return "clients/form";
        clientService.update(id, dto);
        return "redirect:/clients";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        clientService.delete(id);
        return "redirect:/clients";
    }
}
