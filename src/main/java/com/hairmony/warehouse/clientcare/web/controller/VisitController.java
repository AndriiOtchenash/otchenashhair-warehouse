package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.AppointmentService;
import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;
    private final AppointmentService appointmentService;
    private final ClientService clientService;
    private final MessageSource messageSource;

    @GetMapping("/clientcare/clients/{clientId}/visits")
    public String allVisits(@PathVariable Long clientId,
                            @RequestParam(required = false) String returnTo,
                            Model model) {
        model.addAttribute("client", clientService.findById(clientId));
        model.addAttribute("visits", visitService.findByClientId(clientId));
        model.addAttribute("hasUpcomingAppointment",
                appointmentService.getNextUpcomingForClient(clientId).isPresent());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/clients/visits";
    }

    @GetMapping("/clientcare/visits/new")
    public String newForm(@RequestParam Long clientId,
                          @RequestParam(required = false) String returnTo,
                          Model model) {
        VisitDto dto = new VisitDto();
        dto.setClientId(clientId);
        dto.setVisitDate(LocalDate.now());
        model.addAttribute("visit", dto);
        model.addAttribute("client", clientService.findById(clientId));
        model.addAttribute("isLatestVisit", true);
        model.addAttribute("hasUpcomingAppointment",
                appointmentService.getNextUpcomingForClient(clientId).isPresent());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/visits/form";
    }

    @PostMapping("/clientcare/visits/new")
    public String save(@Valid @ModelAttribute("visit") VisitDto dto,
                       BindingResult result,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String returnTo,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("hasUpcomingAppointment",
                    appointmentService.getNextUpcomingForClient(dto.getClientId()).isPresent());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/visits/form";
        }
        Long visitId = visitService.save(dto);
        if ("schedule".equals(action)) {
            return "redirect:/clientcare/appointments?clientId=" + dto.getClientId()
                    + "&linkVisitId=" + visitId
                    + "&returnTo=/clientcare/visits/" + visitId + "/edit";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("visit.success.added", null, LocaleContextHolder.getLocale()));
        return "redirect:" + safeRedirect(returnTo, "/clientcare/clients/" + dto.getClientId());
    }

    @GetMapping("/clientcare/visits/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @RequestParam(required = false) String returnTo,
                           Model model) {
        VisitDto dto = visitService.findById(id);
        model.addAttribute("visit", dto);
        model.addAttribute("client", clientService.findById(dto.getClientId()));
        model.addAttribute("isLatestVisit", visitService.isLatestVisit(id, dto.getClientId()));
        model.addAttribute("hasUpcomingAppointment",
                appointmentService.getNextUpcomingForClient(dto.getClientId()).isPresent());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/visits/form";
    }

    @PostMapping("/clientcare/visits/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("visit") VisitDto dto,
                         BindingResult result,
                         @RequestParam(required = false) String action,
                         @RequestParam(required = false) String returnTo,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("isLatestVisit", visitService.isLatestVisit(id, dto.getClientId()));
            model.addAttribute("hasUpcomingAppointment",
                    appointmentService.getNextUpcomingForClient(dto.getClientId()).isPresent());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/visits/form";
        }
        visitService.update(id, dto);
        if ("schedule".equals(action)) {
            return "redirect:/clientcare/appointments?clientId=" + dto.getClientId()
                    + "&linkVisitId=" + id
                    + "&returnTo=/clientcare/visits/" + id + "/edit";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("visit.success.updated", null, LocaleContextHolder.getLocale()));
        return "redirect:" + safeRedirect(returnTo, "/clientcare/clients/" + dto.getClientId());
    }

    private static String safeRedirect(String returnTo, String fallback) {
        return (returnTo != null && returnTo.matches("^/[^/].*")) ? returnTo : fallback;
    }

    @PostMapping("/clientcare/visits/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam Long clientId,
                         @RequestParam(required = false) String returnTo) {
        visitService.delete(id);
        if ("visits".equals(returnTo)) {
            return "redirect:/clientcare/clients/" + clientId + "/visits";
        }
        return "redirect:/clientcare/clients/" + clientId;
    }
}
