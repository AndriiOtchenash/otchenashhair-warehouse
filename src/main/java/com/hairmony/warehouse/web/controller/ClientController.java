package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;
    private final StockService stockService;
    private final MessageSource messageSource;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("newClient", new ClientDto());
        model.addAttribute("clientsWithMovements", clientService.getClientIdsWithMovements());
        return "clients/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) Long productId) {
        List<StockMovement> movements = stockService.getMovementsByClient(id);
        Set<Long> cancelledIds = stockService.getCancelledMovementIds(
                movements.stream().map(StockMovement::getId).collect(Collectors.toSet()));
        model.addAttribute("client", clientService.findById(id));
        model.addAttribute("movements", movements);
        model.addAttribute("cancelledMovementIds", cancelledIds);
        model.addAttribute("clients", clientService.findAll());
        if (from != null) model.addAttribute("from", from);
        if (productId != null) model.addAttribute("productId", productId);
        return "clients/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model,
                          @RequestParam(required = false) String returnTo,
                          @RequestParam(required = false) Long productId,
                          @RequestParam(required = false) Long movementId) {
        model.addAttribute("client", new ClientDto());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (productId != null) model.addAttribute("productId", productId);
        if (movementId != null) model.addAttribute("movementId", movementId);
        return "clients/form";
    }

    @PostMapping("/new")
    public String createNew(@Valid @ModelAttribute("client") ClientDto dto,
                            BindingResult result,
                            Model model,
                            @RequestParam(required = false) String returnTo,
                            @RequestParam(required = false) Long productId,
                            @RequestParam(required = false) Long movementId) {
        if (result.hasErrors()) {
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (productId != null) model.addAttribute("productId", productId);
            if (movementId != null) model.addAttribute("movementId", movementId);
            return "clients/form";
        }
        ClientDto saved = clientService.save(dto);
        if ("expense".equals(returnTo)) {
            return "redirect:/movements/expense?clientId=" + saved.getId();
        }
        if ("product".equals(returnTo) && productId != null) {
            String query = movementId != null
                    ? "?reopenMovement=" + movementId + "&newClientId=" + saved.getId()
                    : "?newClientId=" + saved.getId();
            return "redirect:/products/" + productId + query;
        }
        if ("history".equals(returnTo)) {
            String query = movementId != null
                    ? "?reopenMovement=" + movementId + "&newClientId=" + saved.getId()
                    : "?newClientId=" + saved.getId();
            return "redirect:/movements/history" + query;
        }
        return "redirect:/clients";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model,
                           @RequestParam(required = false) String returnTo) {
        model.addAttribute("client", clientService.findById(id));
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
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
                         BindingResult result,
                         @RequestParam(required = false) String returnTo) {
        if (result.hasErrors()) return "clients/form";
        clientService.update(id, dto);
        if ("detail".equals(returnTo)) return "redirect:/clients/" + id;
        return "redirect:/clients";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            clientService.delete(id);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), LocaleContextHolder.getLocale()));
        }
        return "redirect:/clients";
    }
}
