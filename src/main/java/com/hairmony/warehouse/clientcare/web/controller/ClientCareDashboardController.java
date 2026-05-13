package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.ClientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/clientcare")
@RequiredArgsConstructor
public class ClientCareDashboardController {

    private final ClientCareService clientCareService;

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("dashboard", clientCareService.getDashboardData());
        return "clientcare/dashboard";
    }
}
