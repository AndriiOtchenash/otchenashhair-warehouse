package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.domain.stock.MovementType;
import com.hairmony.warehouse.domain.stock.WriteOffReason;
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
    public String incomeForm(@RequestParam(required = false) Long productId,
                             @RequestParam(required = false) Long supplierId,
                             @RequestParam(required = false) String returnTo,
                             Model model) {
        StockIncomeDto dto = new StockIncomeDto();
        if (productId != null) dto.setProductId(productId);
        if (supplierId != null) dto.setSupplierId(supplierId);
        model.addAttribute("dto", dto);
        model.addAttribute("products", productService.findAllActive());
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("lastPurchaseInfo", stockService.getLastPurchaseInfoPerProduct());
        model.addAttribute("lastSupplierInfo", stockService.getLastSupplierPerProduct());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "stock/income";
    }

    @PostMapping("/income")
    public String registerIncome(@Valid @ModelAttribute("dto") StockIncomeDto dto,
                                 BindingResult result,
                                 @RequestParam(required = false) String returnTo,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("products", productService.findAllActive());
            model.addAttribute("suppliers", supplierService.findAll());
            model.addAttribute("lastPurchaseInfo", stockService.getLastPurchaseInfoPerProduct());
            model.addAttribute("lastSupplierInfo", stockService.getLastSupplierPerProduct());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "stock/income";
        }
        try {
            stockService.registerIncome(dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("stock.income.success", null, LocaleContextHolder.getLocale()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:" + safeRedirect(returnTo, "/movements/income");
    }

    @GetMapping("/expense")
    public String expenseForm(@RequestParam(required = false) Long productId,
                              @RequestParam(required = false) Long clientId,
                              @RequestParam(required = false) MovementType movementType,
                              @RequestParam(required = false) java.math.BigDecimal quantity,
                              @RequestParam(required = false) String returnTo,
                              Model model) {
        StockExpenseDto dto = new StockExpenseDto();
        dto.setSaleDate(java.time.LocalDate.now());
        if (productId != null) dto.setProductId(productId);
        if (clientId != null) dto.setClientId(clientId);
        if (movementType != null) dto.setMovementType(movementType);
        if (quantity != null) dto.setQuantity(quantity);
        populateExpenseModel(model);
        model.addAttribute("dto", dto);
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (productId != null) {
            productService.findById(productId).ifPresent(p ->
                    model.addAttribute("lockedProductName", p.getName()));
        }
        if (clientId != null && returnTo != null) {
            model.addAttribute("clientLocked", true);
            model.addAttribute("lockedClientName", clientService.findById(clientId).getName());
        }
        return "stock/expense";
    }

    @PostMapping("/expense")
    public String registerExpense(@Valid @ModelAttribute("dto") StockExpenseDto dto,
                                  BindingResult result,
                                  Model model,
                                  @RequestParam(required = false) String returnTo,
                                  RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            populateExpenseModel(model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "stock/expense";
        }
        try {
            stockService.registerExpense(dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage("stock.expense.success", null, LocaleContextHolder.getLocale()));
            return "redirect:" + safeRedirect(returnTo, "/movements/expense");
        } catch (IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            populateExpenseModel(model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "stock/expense";
        }
    }

    private static String safeRedirect(String returnTo, String fallback) {
        return (returnTo != null && returnTo.matches("^/[^/].*")) ? returnTo : fallback;
    }

    private void populateExpenseModel(Model model) {
        model.addAttribute("products", productService.findAllActiveWithStock());
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("expenseTypes", new MovementType[]{
                MovementType.SALE, MovementType.WRITE_OFF, MovementType.ADJUSTMENT
        });
        model.addAttribute("fifoPrices", stockService.getFifoPricesPerProduct());
        model.addAttribute("writeOffReasons", WriteOffReason.values());
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
        if (returnTo != null && returnTo.matches("^/[^/].*")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/movements/history";
    }

    @PostMapping("/{id}/cancel")
    public String cancelMovement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            StockService.CancelResult result = stockService.cancelMovement(id);
            String msgKey = result.isPurchase() ? "movement.cancel.success.purchase" : "movement.cancel.success";
            Object[] args = {result.productName(), result.qtyFormatted(), result.unitLabel(), result.newStockFormatted()};
            redirectAttributes.addFlashAttribute("successMessage",
                    messageSource.getMessage(msgKey, args, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/movements/history";
    }
}
