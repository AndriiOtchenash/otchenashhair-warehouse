package com.hairmony.warehouse.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/keepalive")
    @ResponseBody
    public ResponseEntity<Void> keepalive() {
        return ResponseEntity.ok().build();
    }
}
