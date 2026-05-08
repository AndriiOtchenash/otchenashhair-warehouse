package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.category.Category;
import com.hairmony.warehouse.service.CategoryService;
import com.hairmony.warehouse.web.dto.CategoryDto;
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
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final MessageSource messageSource;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("newCategory", new CategoryDto());
        model.addAttribute("categoriesWithProducts", categoryService.getCategoryIdsWithProducts());
        return "categories/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newCategory") CategoryDto dto,
                         BindingResult result, Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("categoriesWithProducts", categoryService.getCategoryIdsWithProducts());
            return "categories/list";
        }
        Category saved = categoryService.save(dto.getName());
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("category.create.success",
                        new Object[]{saved.getName()}, LocaleContextHolder.getLocale()));
        return "redirect:/categories";
    }

    @GetMapping("/new")
    public String newForm(Model model,
                          @RequestParam(required = false) String returnTo) {
        model.addAttribute("newCategory", new CategoryDto());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "categories/form";
    }

    @PostMapping("/new")
    public String createNew(@Valid @ModelAttribute("newCategory") CategoryDto dto,
                            BindingResult result,
                            Model model,
                            @RequestParam(required = false) String returnTo) {
        if (result.hasErrors()) {
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "categories/form";
        }
        Category saved = categoryService.save(dto.getName());
        if ("product".equals(returnTo)) {
            return "redirect:/products/new?categoryId=" + saved.getId();
        }
        return "redirect:/categories";
    }

    @PostMapping("/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name) {
        categoryService.rename(id, name);
        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String name = categoryService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("category.delete.success",
                            new Object[]{name}, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/categories";
    }
}
