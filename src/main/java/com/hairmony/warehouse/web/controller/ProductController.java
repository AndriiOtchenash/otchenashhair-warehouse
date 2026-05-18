package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.product.Unit;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.service.CategoryService;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.service.ProductService;
import com.hairmony.warehouse.service.StockService;
import com.hairmony.warehouse.web.dto.ProductDto;
import com.hairmony.warehouse.web.dto.ProductLookupDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final StockService stockService;
    private final ClientService clientService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "false") boolean showInactive, Model model) {
        model.addAttribute("products", showInactive ? productService.findAll() : productService.findAllActive());
        model.addAttribute("showInactive", showInactive);
        return "products/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) Long clientId,
                         @RequestParam(required = false) Long supplierId,
                         @RequestParam(defaultValue = "false") boolean showInactive) {
        ProductDto product = productService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        List<StockMovement> movements = stockService.getMovementsByProduct(id);
        Set<Long> cancelledIds = stockService.getCancelledMovementIds(
                movements.stream().map(StockMovement::getId).collect(Collectors.toSet()));
        model.addAttribute("product", product);
        model.addAttribute("stockItems", stockService.getStockItemsByProduct(id));
        model.addAttribute("movements", movements);
        model.addAttribute("cancelledMovementIds", cancelledIds);
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("totalQuantity", stockService.getAvailableQuantity(id));
        if (from != null) model.addAttribute("from", from);
        if (clientId != null) model.addAttribute("clientId", clientId);
        if (supplierId != null) model.addAttribute("supplierId", supplierId);
        if (showInactive) model.addAttribute("showInactive", true);
        return "products/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model,
                          @RequestParam(required = false) String barcode,
                          @RequestParam(required = false) String returnTo,
                          @RequestParam(required = false) String mode,
                          @RequestParam(required = false) Long categoryId) {
        ProductDto dto = new ProductDto();
        if (barcode != null) dto.setBarcode(barcode);
        if (categoryId != null) {
            com.hairmony.warehouse.domain.category.Category cat = new com.hairmony.warehouse.domain.category.Category();
            cat.setId(categoryId);
            dto.setCategory(cat);
        }
        model.addAttribute("product", dto);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("units", Unit.values());
        model.addAttribute("existingBrands", productService.findAllBrands());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (mode != null) model.addAttribute("mode", mode);
        return "products/form";
    }

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("product") ProductDto dto,
                       BindingResult result,
                       Model model,
                       @RequestParam(required = false) String returnTo,
                       @RequestParam(required = false) String mode) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("units", Unit.values());
            model.addAttribute("existingBrands", productService.findAllBrands());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (mode != null) model.addAttribute("mode", mode);
            return "products/form";
        }
        ProductDto saved = productService.save(dto);
        if ("scan".equals(returnTo) && mode != null) {
            String path = "income".equals(mode) ? "/movements/income" : "/movements/expense";
            return "redirect:" + path + "?productId=" + saved.getId();
        }
        if ("income".equals(returnTo)) {
            return "redirect:/movements/income?productId=" + saved.getId();
        }
        if ("expense".equals(returnTo)) {
            return "redirect:/movements/expense?productId=" + saved.getId();
        }
        return "redirect:/products";
    }

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<ProductLookupDto>> search(@RequestParam String q) {
        return ResponseEntity.ok(productService.searchForScan(q));
    }

    @GetMapping("/by-barcode")
    @ResponseBody
    public ResponseEntity<ProductLookupDto> byBarcode(@RequestParam String code) {
        return productService.findByBarcode(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/assign-barcode")
    @ResponseBody
    public ResponseEntity<?> assignBarcode(@PathVariable Long id, @RequestParam String code) {
        try {
            return ResponseEntity.ok(productService.assignBarcode(id, code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model,
                           @RequestParam(required = false) String returnTo) {
        ProductDto product = productService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + id));
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("units", Unit.values());
        model.addAttribute("existingBrands", productService.findAllBrands());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "products/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("product") ProductDto dto,
                         BindingResult result,
                         Model model,
                         @RequestParam(required = false) String returnTo) {
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            model.addAttribute("units", Unit.values());
            model.addAttribute("existingBrands", productService.findAllBrands());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "products/form";
        }
        productService.update(id, dto);
        if ("detail".equals(returnTo)) return "redirect:/products/" + id;
        return "redirect:/products";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             @RequestParam(required = false) String deactivationReason,
                             org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (deactivationReason != null && deactivationReason.length() > 200) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Причина деактивації не може перевищувати 200 символів.");
            return "redirect:/products";
        }
        productService.deactivate(id, deactivationReason);
        return "redirect:/products";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id) {
        productService.restore(id);
        return "redirect:/products?showInactive=true";
    }
}
