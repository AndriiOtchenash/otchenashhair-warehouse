package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.service.*;
import com.hairmony.warehouse.web.dto.*;
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
@RequestMapping("/movements")
public class StockController {

    private final StockService stockService;
    private final ProductService productService;
    private final SupplierService supplierService;
    private final ClientService clientService;
    private final MessageSource messageSource;

    @GetMapping("/income")
    public String incomeForm(@RequestParam(required = false) Long productId, Model model) {
        StockIncomeDto dto = new StockIncomeDto();
        if (productId != null) dto.setProductId(productId);
        model.addAttribute("dto", dto);
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("suppliers", supplierService.findAll());
        return "stock/income";
    }

    @PostMapping("/income")
    public String registerIncome(@Valid @ModelAttribute("dto") StockIncomeDto dto,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("products", productService.findAllActive());
            model.addAttribute("suppliers", supplierService.findAll());
            return "stock/income";
        }
        try {
            stockService.registerIncome(dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("stock.income.success", null, LocaleContextHolder.getLocale()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/movements/income";
    }

    @GetMapping("/expense")
    public String expenseForm(@RequestParam(required = false) Long productId,
                              @RequestParam(required = false) Long clientId,
                              Model model) {
        StockExpenseDto dto = new StockExpenseDto();
        if (productId != null) dto.setProductId(productId);
        if (clientId != null) dto.setClientId(clientId);
        model.addAttribute("dto", dto);
        model.addAttribute("products", productService.findAllActiveWithStock());
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("expenseTypes", new MovementType[]{
                MovementType.SALE, MovementType.WRITE_OFF, MovementType.ADJUSTMENT
        });
        return "stock/expense";
    }

    @PostMapping("/expense")
    public String registerExpense(@Valid @ModelAttribute("dto") StockExpenseDto dto,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("products", productService.findAllActiveWithStock());
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("expenseTypes", new MovementType[]{
                    MovementType.SALE, MovementType.WRITE_OFF, MovementType.ADJUSTMENT
            });
            return "stock/expense";
        }
        try {
            stockService.registerExpense(dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("stock.expense.success", null, LocaleContextHolder.getLocale()));
            return "redirect:/movements/expense";
        } catch (IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("products", productService.findAllActiveWithStock());
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("expenseTypes", new MovementType[]{
                    MovementType.SALE, MovementType.WRITE_OFF, MovementType.ADJUSTMENT
            });
            return "stock/expense";
        }
    }

    @PostMapping("/{id}/edit")
    public String editMovement(@PathVariable Long id,
                               @RequestParam(required = false) Long clientId,
                               @RequestParam(required = false) String notes,
                               @RequestParam(required = false) String returnTo,
                               RedirectAttributes redirectAttributes) {
        try {
            stockService.updateMovementMeta(id, clientId, notes);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("movement.edit.success", null, LocaleContextHolder.getLocale()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        if (returnTo != null && returnTo.startsWith("/")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/movements/history";
    }

    @PostMapping("/{id}/cancel")
    public String cancelMovement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            stockService.cancelMovement(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("movement.cancel.success", null, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/movements/history";
    }
}
