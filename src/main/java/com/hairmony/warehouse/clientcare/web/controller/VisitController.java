package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.AppointmentService;
import com.hairmony.warehouse.clientcare.service.GiftCertificateService;
import com.hairmony.warehouse.clientcare.service.SalonServiceService;
import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.SalonServiceDto;
import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.clientcare.web.dto.VisitJournalRowDto;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.domain.appointment.PaymentMethod;
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

import org.springframework.http.ResponseEntity;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;
    private final AppointmentService appointmentService;
    private final SalonServiceService salonServiceService;
    private final ClientService clientService;
    private final GiftCertificateService giftCertificateService;
    private final MessageSource messageSource;

    @GetMapping("/clientcare/visits")
    public String journal(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(required = false) Long clientId,
                          @RequestParam(required = false) Long serviceId,
                          @RequestParam(required = false) Boolean paid,
                          Model model) {
        if (from != null && to != null && to.isBefore(from)) {
            LocalDate tmp = from; from = to; to = tmp;
        }
        List<VisitJournalRowDto> rows = visitService.findForJournal(from, to, clientId, serviceId, paid);

        Map<Long, String> serviceNames = salonServiceService.findAll().stream()
                .collect(Collectors.toMap(SalonServiceDto::getId, SalonServiceDto::getName));

        long unpaidCount = rows.stream()
                .filter(r -> !r.isPaid() && (r.getPriceAtTime() != null || r.getPaymentMethod() != null))
                .count();
        BigDecimal revenue = rows.stream()
                .filter(r -> r.isPaid() && r.getPriceAtTime() != null)
                .map(VisitJournalRowDto::getPriceAtTime)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("visits", rows);
        model.addAttribute("serviceNames", serviceNames);
        model.addAttribute("services", salonServiceService.findAll());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("clientId", clientId);
        model.addAttribute("serviceId", serviceId);
        model.addAttribute("paid", paid);
        model.addAttribute("kpiTotal", rows.size());
        model.addAttribute("kpiRevenue", revenue);
        model.addAttribute("kpiUnpaid", unpaidCount);
        return "clientcare/visits/journal";
    }

    /** AJAX endpoint — checks if a gift certificate code is valid (ACTIVE) for redemption.
     *  Returns {@code {valid: true/false, recipientName: "..." | null, serviceName: "..." | null}}. */
    @GetMapping("/clientcare/visits/check-certificate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkCertificate(@RequestParam String code) {
        var cert = giftCertificateService.findIfValid(code);
        Map<String, Object> body = new HashMap<>();
        body.put("valid", cert.isPresent());
        body.put("recipientName", cert.map(c -> c.getRecipientName()).orElse(null));
        body.put("serviceName",   cert.map(c -> c.getServiceName()).orElse(null));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/clientcare/clients/{clientId}/visits")
    public String allVisits(@PathVariable Long clientId,
                            @RequestParam(required = false) String returnTo,
                            Model model) {
        model.addAttribute("client", clientService.findById(clientId));
        model.addAttribute("visits", visitService.findByClientId(clientId));
        model.addAttribute("hasUpcomingAppointment",
                appointmentService.getNextUpcomingForClient(clientId).isPresent());
        model.addAttribute("serviceNames",
                salonServiceService.findAll().stream()
                        .collect(Collectors.toMap(SalonServiceDto::getId, SalonServiceDto::getName)));
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/clients/visits";
    }

    @GetMapping("/clientcare/visits/new")
    public String newForm(@RequestParam Long clientId,
                          @RequestParam(required = false) String returnTo,
                          @RequestParam(required = false) Long completeAppointmentId,
                          Model model) {
        VisitDto dto = new VisitDto();
        dto.setClientId(clientId);
        dto.setVisitDate(LocalDate.now());
        model.addAttribute("visit", dto);
        model.addAttribute("client", clientService.findById(clientId));
        model.addAttribute("isLatestVisit", true);
        model.addAttribute("hasUpcomingAppointment",
                appointmentService.getNextUpcomingForClient(clientId).isPresent());
        populateFormModel(model);
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (completeAppointmentId != null) model.addAttribute("completeAppointmentId", completeAppointmentId);
        return "clientcare/visits/form";
    }

    @PostMapping("/clientcare/visits/new")
    public String save(@Valid @ModelAttribute("visit") VisitDto dto,
                       BindingResult result,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String returnTo,
                       @RequestParam(required = false) Long completeAppointmentId,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("hasUpcomingAppointment",
                    appointmentService.getNextUpcomingForClient(dto.getClientId()).isPresent());
            populateFormModel(model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (completeAppointmentId != null) model.addAttribute("completeAppointmentId", completeAppointmentId);
            return "clientcare/visits/form";
        }
        Long visitId;
        try {
            visitId = visitService.save(dto);
        } catch (IllegalStateException e) {
            rejectCertificateError(e, result, model);
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("hasUpcomingAppointment",
                    appointmentService.getNextUpcomingForClient(dto.getClientId()).isPresent());
            populateFormModel(model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (completeAppointmentId != null) model.addAttribute("completeAppointmentId", completeAppointmentId);
            return "clientcare/visits/form";
        }
        // Mark the source appointment as COMPLETED only after the visit is successfully saved.
        if (completeAppointmentId != null && !"schedule".equals(action)) {
            appointmentService.changeStatus(completeAppointmentId, AppointmentStatus.COMPLETED);
            visitService.unlinkCompletedAppointment(completeAppointmentId);
        }
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
        populateFormModel(model);
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
            populateFormModel(model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/visits/form";
        }
        try {
            visitService.update(id, dto);
        } catch (IllegalStateException e) {
            rejectCertificateError(e, result, model);
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("isLatestVisit", visitService.isLatestVisit(id, dto.getClientId()));
            model.addAttribute("hasUpcomingAppointment",
                    appointmentService.getNextUpcomingForClient(dto.getClientId()).isPresent());
            populateFormModel(model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/visits/form";
        }
        if ("schedule".equals(action)) {
            return "redirect:/clientcare/appointments?clientId=" + dto.getClientId()
                    + "&linkVisitId=" + id
                    + "&returnTo=/clientcare/visits/" + id + "/edit";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("visit.success.updated", null, LocaleContextHolder.getLocale()));
        return "redirect:" + safeRedirect(returnTo, "/clientcare/clients/" + dto.getClientId());
    }

    private void populateFormModel(Model model) {
        model.addAttribute("activeServices", salonServiceService.findAllActive());
        model.addAttribute("paymentMethods", PaymentMethod.values());
    }

    private void rejectCertificateError(IllegalStateException e, BindingResult result, Model model) {
        String msg = e.getMessage();
        if (msg != null && msg.contains(":")) {
            String[] parts = msg.split(":", 2);
            result.rejectValue("certificateCode", parts[0], new Object[]{parts[1]}, parts[0]);
        } else {
            result.reject("visit.certificate.error");
        }
    }

    private static String safeRedirect(String returnTo, String fallback) {
        return (returnTo != null && returnTo.matches("^/[^/].*")) ? returnTo : fallback;
    }

    @PostMapping("/clientcare/visits/{id}/skip-next")
    public String skipNext(@PathVariable Long id,
                           @RequestParam(required = false) String returnTo) {
        visitService.skipNextVisit(id);
        String redirect = "/clientcare/visits/" + id + "/edit";
        if (returnTo != null && returnTo.matches("^/[^/].*")) redirect += "?returnTo=" + returnTo;
        return "redirect:" + redirect;
    }

    @PostMapping("/clientcare/visits/{id}/unskip-next")
    public String unskipNext(@PathVariable Long id,
                             @RequestParam(required = false) String returnTo,
                             @RequestParam(required = false) String redirectTo) {
        visitService.unskipNextVisit(id);
        // redirectTo: used by followups page — go directly there instead of visit edit
        if (redirectTo != null && redirectTo.matches("^/[^/].*")) return "redirect:" + redirectTo;
        String redirect = "/clientcare/visits/" + id + "/edit";
        if (returnTo != null && returnTo.matches("^/[^/].*")) redirect += "?returnTo=" + returnTo;
        return "redirect:" + redirect;
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
