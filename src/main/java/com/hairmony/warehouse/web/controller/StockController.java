package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.service.*;
import com.hairmony.warehouse.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
            redirectAttributes.addFlashAttribute("successMessage", "Прихід товару зареєстровано");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/movements/income";
    }

    @GetMapping("/expense")
    public String expenseForm(@RequestParam(required = false) Long productId, Model model) {
        StockExpenseDto dto = new StockExpenseDto();
        if (productId != null) dto.setProductId(productId);
        model.addAttribute("dto", dto);
        model.addAttribute("products", productService.findAllActive());
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
            model.addAttribute("products", productService.findAllActive());
            model.addAttribute("clients", clientService.findAll());
            model.addAttribute("expenseTypes", new MovementType[]{
                    MovementType.SALE, MovementType.WRITE_OFF, MovementType.ADJUSTMENT
            });
            return "stock/expense";
        }
        try {
            stockService.registerExpense(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Витрату зареєстровано");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/movements/expense";
    }
}
