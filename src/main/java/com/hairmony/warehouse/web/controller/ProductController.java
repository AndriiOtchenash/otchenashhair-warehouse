package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.product.Unit;
import com.hairmony.warehouse.service.CategoryService;
import com.hairmony.warehouse.service.ProductService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.ProductDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final StockService stockService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean showInactive, Model model) {
        model.addAttribute("products", showInactive ? productService.findAll() : productService.findAllActive());
        model.addAttribute("showInactive", showInactive);
        return "products/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProductDto product = productService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        model.addAttribute("product", product);
        model.addAttribute("stockItems", stockService.getStockItemsByProduct(id));
        model.addAttribute("movements", stockService.getMovementsByProduct(id));
        model.addAttribute("totalQuantity", stockService.getAvailableQuantity(id));
        return "products/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("product", new ProductDto());
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("units", Unit.values());
        model.addAttribute("existingBrands", productService.findAllBrands());
        return "products/form";
    }

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("product") ProductDto dto,
                       BindingResult result,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("units", Unit.values());
            model.addAttribute("existingBrands", productService.findAllBrands());
            return "products/form";
        }
        productService.save(dto);
        return "redirect:/products";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        ProductDto product = productService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("units", Unit.values());
        model.addAttribute("existingBrands", productService.findAllBrands());
        return "products/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("product") ProductDto dto,
                         BindingResult result,
                         Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("units", Unit.values());
            model.addAttribute("existingBrands", productService.findAllBrands());
            return "products/form";
        }
        productService.update(id, dto);
        return "redirect:/products";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id) {
        productService.deactivate(id);
        return "redirect:/products";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id) {
        productService.restore(id);
        return "redirect:/products?showInactive=true";
    }
}
