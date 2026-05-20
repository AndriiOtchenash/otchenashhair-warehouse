package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.SalonServiceService;
import com.hairmony.warehouse.clientcare.web.dto.SalonServiceDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/clientcare/services")
public class SalonServiceController {

    private final SalonServiceService salonServiceService;
    private final MessageSource messageSource;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("services", salonServiceService.findAll());
        model.addAttribute("newService", new SalonServiceDto());
        return "clientcare/services/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newService") SalonServiceDto dto,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("services", salonServiceService.findAll());
            model.addAttribute("showForm", true);
            return "clientcare/services/list";
        }
        SalonServiceDto saved = salonServiceService.save(dto);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("service.success.created",
                        new Object[]{saved.getName()}, LocaleContextHolder.getLocale()));
        return "redirect:/clientcare/services";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("service", salonServiceService.findById(id));
        return "clientcare/services/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("service") SalonServiceDto dto,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "clientcare/services/form";
        }
        dto.setId(id);
        salonServiceService.update(dto);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("service.success.updated",
                        new Object[]{dto.getName()}, LocaleContextHolder.getLocale()));
        return "redirect:/clientcare/services";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        SalonServiceDto dto = salonServiceService.findById(id);
        salonServiceService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("service.success.deactivated",
                        new Object[]{dto.getName()}, LocaleContextHolder.getLocale()));
        return "redirect:/clientcare/services";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        SalonServiceDto dto = salonServiceService.findById(id);
        salonServiceService.activate(id);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("service.success.activated",
                        new Object[]{dto.getName()}, LocaleContextHolder.getLocale()));
        return "redirect:/clientcare/services";
    }
}
