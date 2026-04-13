package com.hairmony.warehouse.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ReportsController {

    @GetMapping("/reports")
    public String reports() {
        return "reports/index";
    }
}
