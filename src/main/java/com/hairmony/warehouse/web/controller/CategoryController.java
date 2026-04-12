package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.CategoryService;
import com.hairmony.warehouse.web.dto.CategoryDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("newCategory", new CategoryDto());
        return "categories/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newCategory") CategoryDto dto,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            return "categories/list";
        }
        categoryService.save(dto.getName());
        return "redirect:/categories";
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name) {
        categoryService.rename(id, name);
        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        categoryService.delete(id);
        return "redirect:/categories";
    }
}
