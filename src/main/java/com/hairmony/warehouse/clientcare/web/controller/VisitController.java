package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;


@Controller
@RequestMapping("/clientcare/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;
    private final ClientService clientService;

    @GetMapping("/new")
    public String newForm(@RequestParam Long clientId, Model model) {
        VisitDto dto = new VisitDto();
        dto.setClientId(clientId);
        dto.setVisitDate(LocalDate.now());
        model.addAttribute("visit", dto);
        model.addAttribute("client", clientService.findById(clientId));
        return "clientcare/visits/form";
    }

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("visit") VisitDto dto,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        validateNextVisitDate(dto, result);
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            return "clientcare/visits/form";
        }
        visitService.save(dto);
        redirectAttributes.addFlashAttribute("successMessage", "visit.success.added");
        return "redirect:/clientcare/clients/" + dto.getClientId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        VisitDto dto = visitService.findById(id);
        model.addAttribute("visit", dto);
        model.addAttribute("client", clientService.findById(dto.getClientId()));
        return "clientcare/visits/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("visit") VisitDto dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        validateNextVisitDate(dto, result);
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            return "clientcare/visits/form";
        }
        visitService.update(id, dto);
        redirectAttributes.addFlashAttribute("successMessage", "visit.success.updated");
        return "redirect:/clientcare/clients/" + dto.getClientId();
    }

    private void validateNextVisitDate(VisitDto dto, BindingResult result) {
        if (dto.getNextVisitDate() != null && dto.getVisitDate() != null
                && dto.getNextVisitDate().isBefore(dto.getVisitDate())) {
            result.rejectValue("nextVisitDate", "visit.error.nextVisitDateBeforeVisitDate");
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam Long clientId) {
        visitService.delete(id);
        return "redirect:/clientcare/clients/" + clientId;
    }
}
