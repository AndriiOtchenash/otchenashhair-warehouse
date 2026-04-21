package com.hairmony.warehouse.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/scan")
public class ScanController {

    @GetMapping
    public String scan() {
        return "scan/scan";
    }
}
