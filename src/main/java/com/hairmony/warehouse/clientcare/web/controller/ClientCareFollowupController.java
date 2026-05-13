package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.ClientCareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/clientcare/followups")
@RequiredArgsConstructor
public class ClientCareFollowupController {

    private final ClientCareService clientCareService;

    @GetMapping
    public String followups(@RequestParam(required = false, defaultValue = "0") int minDays,
                            Model model) {
        model.addAttribute("clients", clientCareService.getFollowupQueue());
        model.addAttribute("activeMinDays", minDays);
        return "clientcare/followups";
    }
}
