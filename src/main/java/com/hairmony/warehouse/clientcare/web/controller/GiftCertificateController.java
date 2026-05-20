package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.GiftCertificateService;
import com.hairmony.warehouse.clientcare.web.dto.GiftCertificateFormDto;
import com.hairmony.warehouse.clientcare.service.SalonServiceService;
import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
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
@RequestMapping("/clientcare/gift-certificates")
public class GiftCertificateController {

    private final GiftCertificateService giftCertificateService;
    private final ClientService clientService;
    private final SalonServiceService salonServiceService;
    private final MessageSource messageSource;

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) Long clientId,
                       Model model) {

        if (clientId != null) {
            // ── Client view (from client detail) — no status filter, all statuses ──
            model.addAttribute("certificates", giftCertificateService.findForClient(clientId));
            model.addAttribute("filterClient", clientService.findById(clientId));
            model.addAttribute("filterClientId", clientId);
        } else {
            // ── Main list — status filter only; client search is client-side ──
            String effectiveStatus = (status == null) ? "ACTIVE" : status;
            if (!effectiveStatus.isBlank()) {
                GiftCertificateStatus filter = GiftCertificateStatus.valueOf(effectiveStatus);
                model.addAttribute("certificates", giftCertificateService.findAllByStatus(filter));
            } else {
                model.addAttribute("certificates", giftCertificateService.findAll());
            }
            model.addAttribute("statusFilter", effectiveStatus);
            model.addAttribute("statuses", GiftCertificateStatus.values());
        }
        return "clientcare/gift-certificates/list";
    }

    // ── New form ──────────────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long purchaserId,
                          @RequestParam(required = false) String returnTo,
                          Model model) {
        GiftCertificateFormDto dto = new GiftCertificateFormDto();
        dto.setPurchaserClientId(purchaserId);
        dto.setExpiresAt(LocalDate.now().plusMonths(6));
        populateFormModel(model);
        model.addAttribute("form", dto);
        model.addAttribute("returnTo", returnTo);
        return "clientcare/gift-certificates/form";
    }

    @PostMapping("/new")
    public String issue(@Valid @ModelAttribute("form") GiftCertificateFormDto form,
                        BindingResult bindingResult,
                        @RequestParam(required = false) String returnTo,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model);
            model.addAttribute("returnTo", returnTo);
            return "clientcare/gift-certificates/form";
        }
        GiftCertificate cert = giftCertificateService.issue(form);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("gift.success.issued",
                        new Object[]{cert.getCode()}, LocaleContextHolder.getLocale()));
        return "redirect:" + safeRedirect(returnTo, "/clientcare/gift-certificates");
    }

    // ── Detail ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) String returnTo,
                         Model model) {
        model.addAttribute("cert", giftCertificateService.findById(id));
        model.addAttribute("returnTo", returnTo);
        return "clientcare/gift-certificates/detail";
    }

    // ── Restore (CANCELLED → ACTIVE) ─────────────────────────────────────────

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id,
                          @RequestParam(required = false) String returnTo,
                          RedirectAttributes redirectAttributes) {
        try {
            GiftCertificate cert = giftCertificateService.restore(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("gift.success.restored",
                            new Object[]{cert.getCode()}, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + safeRedirect(returnTo, "/clientcare/gift-certificates");
    }

    // ── Delete (hard delete, blocked for REDEEMED) ────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes redirectAttributes) {
        String code = giftCertificateService.findById(id).getCode();
        try {
            giftCertificateService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("gift.success.deleted",
                            new Object[]{code}, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messageSource.getMessage(e.getMessage(), null, e.getMessage(), LocaleContextHolder.getLocale()));
        }
        return "redirect:" + safeRedirect(returnTo, "/clientcare/gift-certificates");
    }

    // ── Cancel (ACTIVE → CANCELLED) ───────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes redirectAttributes) {
        try {
            GiftCertificate cert = giftCertificateService.cancel(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("gift.success.cancelled",
                            new Object[]{cert.getCode()}, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + safeRedirect(returnTo, "/clientcare/gift-certificates");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void populateFormModel(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("services", salonServiceService.findAllActive());
    }

    private static String safeRedirect(String returnTo, String fallback) {
        return (returnTo != null && returnTo.matches("^/[^/].*")) ? returnTo : fallback;
    }
}
