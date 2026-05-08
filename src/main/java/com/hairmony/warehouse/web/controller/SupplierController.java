package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.SupplierService;
import com.hairmony.warehouse.web.dto.SupplierDto;
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
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;
    private final MessageSource messageSource;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("newSupplier", new SupplierDto());
        model.addAttribute("suppliersWithMovements", supplierService.getSupplierIdsWithMovements());
        return "suppliers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model,
                          @RequestParam(required = false) String returnTo) {
        model.addAttribute("supplier", new SupplierDto());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "suppliers/form";
    }

    @PostMapping("/new")
    public String createNew(@Valid @ModelAttribute("supplier") SupplierDto dto,
                            BindingResult result,
                            Model model,
                            @RequestParam(required = false) String returnTo) {
        if (result.hasErrors()) {
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "suppliers/form";
        }
        SupplierDto saved = supplierService.save(dto);
        if ("income".equals(returnTo)) {
            return "redirect:/movements/income?supplierId=" + saved.getId();
        }
        return "redirect:/suppliers";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("supplier", supplierService.findById(id));
        return "suppliers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newSupplier") SupplierDto dto,
                         BindingResult result, Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("suppliers", supplierService.findAll());
            return "suppliers/list";
        }
        SupplierDto saved = supplierService.save(dto);
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("supplier.create.success",
                        new Object[]{saved.getName()}, LocaleContextHolder.getLocale()));
        return "redirect:/suppliers";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("supplier") SupplierDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "suppliers/form";
        }
        supplierService.update(id, dto);
        return "redirect:/suppliers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String name = supplierService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("supplier.delete.success",
                            new Object[]{name}, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/suppliers";
    }
}
