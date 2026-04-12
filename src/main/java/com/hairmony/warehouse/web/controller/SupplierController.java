package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.SupplierService;
import com.hairmony.warehouse.web.dto.SupplierDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("newSupplier", new SupplierDto());
        return "suppliers/list";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("supplier", supplierService.findById(id));
        return "suppliers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newSupplier") SupplierDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("suppliers", supplierService.findAll());
            return "suppliers/list";
        }
        supplierService.save(dto);
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
    public String delete(@PathVariable Long id) {
        supplierService.delete(id);
        return "redirect:/suppliers";
    }
}
