package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.clientcare.service.AppointmentService;
import com.hairmony.warehouse.clientcare.service.GiftCertificateService;
import com.hairmony.warehouse.clientcare.service.SalonServiceService;
import com.hairmony.warehouse.clientcare.service.ScalpPhotoService;
import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.SalonServiceDto;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping({"/clients", "/clientcare/clients"})
public class ClientController {

    private final ClientService clientService;
    private final StockService stockService;
    private final ScalpPhotoService scalpPhotoService;
    private final VisitService visitService;
    private final AppointmentService appointmentService;
    private final GiftCertificateService giftCertificateService;
    private final SalonServiceService salonServiceService;
    private final MessageSource messageSource;

    private boolean isClientCare(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/clientcare");
    }

    private void addAppointmentBadgeAttrs(Long clientId, Model model) {
        var upcoming = appointmentService.getAllUpcomingForClient(clientId);
        if (!upcoming.isEmpty()) model.addAttribute("upcomingAppointments", upcoming);
        if (upcoming.isEmpty()) {
            appointmentService.getLatestOverdueForClient(clientId).ifPresent(a -> {
                // Suppress badge if the client already has a visit on or after the overdue appointment date
                // (means they came in afterwards — the missed record is no longer actionable)
                boolean resolvedByVisit = visitService.hasVisitOnOrAfter(
                        clientId, a.getStartAt().toLocalDate());
                if (!resolvedByVisit) {
                    model.addAttribute("overdueAppointment", a);
                }
            });
        }
    }

    @GetMapping
    public String list(Model model, HttpServletRequest request) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("newClient", new ClientDto());
        model.addAttribute("clientsNotDeletable", clientService.getClientIdsNotDeletable());
        model.addAttribute("clientsWithUpcoming",
                appointmentService.getClientIdsWithUpcomingAppointments());
        model.addAttribute("clientsWithOverdue",
                appointmentService.getClientIdsWithOverdueAppointments());
        model.addAttribute("clientsWithUnresolvedNextVisit",
                visitService.getClientIdsWithUnresolvedNextVisit());
        model.addAttribute("clientsWithUnpaidVisits",
                visitService.getClientIdsWithUnpaidVisits());
        return isClientCare(request) ? "clientcare/clients/list" : "clients/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) Long productId,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String returnTo,
                         HttpServletRequest request) {
        List<StockMovement> movements = stockService.getMovementsByClient(id);
        Set<Long> cancelledIds = stockService.getCancelledMovementIds(
                movements.stream().map(StockMovement::getId).collect(Collectors.toSet()));
        model.addAttribute("client", clientService.findById(id));
        model.addAttribute("movements", movements);
        model.addAttribute("cancelledMovementIds", cancelledIds);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("scalpPhotos", scalpPhotoService.findByClientId(id));
        model.addAttribute("visits", visitService.findByClientId(id));
        model.addAttribute("serviceNames",
                salonServiceService.findAll().stream()
                        .collect(Collectors.toMap(SalonServiceDto::getId, SalonServiceDto::getName)));
        model.addAttribute("giftCertificates", giftCertificateService.findForClient(id));
        if (from != null) model.addAttribute("from", from);
        if (productId != null) model.addAttribute("productId", productId);

        addAppointmentBadgeAttrs(id, model);

        if ("appointments".equals(from)) {
            String backUrl = "/clientcare/appointments" + (date != null ? "?date=" + date : "");
            model.addAttribute("backUrl", backUrl);
            model.addAttribute("editUrl", "/clientcare/clients/" + id + "/edit?returnTo=detail");
            model.addAttribute("currentPageUrl", "/clientcare/clients/" + id + "?from=appointments" + (date != null ? "&date=" + date : ""));
            model.addAttribute("appointmentsByDate", appointmentService.getAppointmentsByDateForClient(id));
            return "clientcare/clients/detail";
        }
        if ("clientcare".equals(from)) {
            model.addAttribute("backUrl", "/clientcare/followups");
            model.addAttribute("editUrl", "/clients/" + id + "/edit?returnTo=detail");
            model.addAttribute("currentPageUrl", "/clients/" + id + "?from=clientcare");
            model.addAttribute("appointmentsByDate", appointmentService.getAppointmentsByDateForClient(id));
            return "clientcare/clients/detail";
        }
        if (isClientCare(request)) {
            String backUrl = safeReturnTo(returnTo, "/clientcare/clients");
            model.addAttribute("backUrl", backUrl);
            model.addAttribute("editUrl", "/clientcare/clients/" + id + "/edit?returnTo=detail");
            model.addAttribute("currentPageUrl", "/clientcare/clients/" + id +
                    (returnTo != null ? "?returnTo=" + returnTo : ""));
            model.addAttribute("appointmentsByDate", appointmentService.getAppointmentsByDateForClient(id));
            return "clientcare/clients/detail";
        }
        model.addAttribute("backUrl", "/clients");
        model.addAttribute("editUrl", "/clients/" + id + "/edit?returnTo=detail");
        model.addAttribute("currentPageUrl", "/clients/" + id);
        return "clients/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model,
                          @RequestParam(required = false) String returnTo,
                          @RequestParam(required = false) Long productId,
                          @RequestParam(required = false) Long movementId,
                          @RequestParam(required = false) String movementType,
                          @RequestParam(required = false) String quantity) {
        model.addAttribute("client", new ClientDto());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (productId != null) model.addAttribute("productId", productId);
        if (movementId != null) model.addAttribute("movementId", movementId);
        if (movementType != null) model.addAttribute("movementType", movementType);
        if (quantity != null) model.addAttribute("quantity", quantity);
        return "clients/form";
    }

    @PostMapping("/new")
    public String createNew(@Valid @ModelAttribute("client") ClientDto dto,
                            BindingResult result,
                            Model model,
                            @RequestParam(required = false) String returnTo,
                            @RequestParam(required = false) Long productId,
                            @RequestParam(required = false) Long movementId,
                            @RequestParam(required = false) String movementType,
                            @RequestParam(required = false) String quantity,
                            HttpServletRequest request) {
        if (result.hasErrors()) {
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (productId != null) model.addAttribute("productId", productId);
            if (movementId != null) model.addAttribute("movementId", movementId);
            if (movementType != null) model.addAttribute("movementType", movementType);
            if (quantity != null) model.addAttribute("quantity", quantity);
            return "clients/form";
        }
        ClientDto saved = clientService.save(dto);
        if ("expense".equals(returnTo)) {
            StringBuilder redirect = new StringBuilder("/movements/expense?clientId=").append(saved.getId());
            if (productId != null) redirect.append("&productId=").append(productId);
            if (movementType != null) redirect.append("&movementType=").append(movementType);
            if (quantity != null) redirect.append("&quantity=").append(quantity);
            return "redirect:" + redirect;
        }
        if ("product".equals(returnTo) && productId != null) {
            String query = movementId != null
                    ? "?reopenMovement=" + movementId + "&newClientId=" + saved.getId()
                    : "?newClientId=" + saved.getId();
            return "redirect:/products/" + productId + query;
        }
        if ("gift-certificate".equals(returnTo)) {
            return "redirect:/clientcare/gift-certificates/new?purchaserId=" + saved.getId();
        }
        if ("history".equals(returnTo)) {
            String query = movementId != null
                    ? "?reopenMovement=" + movementId + "&newClientId=" + saved.getId()
                    : "?newClientId=" + saved.getId();
            return "redirect:/movements/history" + query;
        }
        return isClientCare(request) ? "redirect:/clientcare/clients" : "redirect:/clients";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model,
                           @RequestParam(required = false) String returnTo,
                           HttpServletRequest request) {
        model.addAttribute("client", clientService.findById(id));
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (isClientCare(request)) model.addAttribute("clientCareContext", true);
        return "clients/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newClient") ClientDto dto,
                         BindingResult result, Model model,
                         HttpServletRequest request) {
        if (result.hasErrors()) {
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("clientsNotDeletable", clientService.getClientIdsNotDeletable());
            model.addAttribute("clientsWithUpcoming",
                    appointmentService.getClientIdsWithUpcomingAppointments());
            model.addAttribute("clientsWithOverdue",
                    appointmentService.getClientIdsWithOverdueAppointments());
            model.addAttribute("clientsWithUnresolvedNextVisit",
                    visitService.getClientIdsWithUnresolvedNextVisit());
            model.addAttribute("clientsWithUnpaidVisits",
                    visitService.getClientIdsWithUnpaidVisits());
            return isClientCare(request) ? "clientcare/clients/list" : "clients/list";
        }
        clientService.save(dto);
        return isClientCare(request) ? "redirect:/clientcare/clients" : "redirect:/clients";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("client") ClientDto dto,
                         BindingResult result,
                         @RequestParam(required = false) String returnTo,
                         HttpServletRequest request) {
        if (result.hasErrors()) return "clients/form";
        clientService.update(id, dto);
        if ("detail".equals(returnTo)) {
            return isClientCare(request)
                    ? "redirect:/clientcare/clients/" + id
                    : "redirect:/clients/" + id;
        }
        return isClientCare(request) ? "redirect:/clientcare/clients" : "redirect:/clients";
    }

    private static String safeReturnTo(String returnTo, String fallback) {
        return (returnTo != null && returnTo.matches("^/[^/].*")) ? returnTo : fallback;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes,
                         HttpServletRequest request) {
        try {
            clientService.delete(id);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), LocaleContextHolder.getLocale()));
        }
        return isClientCare(request) ? "redirect:/clientcare/clients" : "redirect:/clients";
    }
}
