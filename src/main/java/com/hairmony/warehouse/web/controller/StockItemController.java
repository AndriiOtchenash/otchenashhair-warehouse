package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/stock/items")
public class StockItemController {

    private final StockService stockService;

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                       @RequestParam(required = false) String batchNumber,
                       @RequestParam(required = false) BigDecimal purchasePrice,
                       @RequestParam Long productId) {
        stockService.updateStockItem(id, expiryDate, batchNumber, purchasePrice);
        return "redirect:/products/" + productId;
    }
}
