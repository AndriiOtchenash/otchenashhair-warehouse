package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
@RequestMapping("/ai")
public class AiController {

    private final AiAssistantService aiAssistantService;

    @GetMapping
    public String aiPage() {
        return "ai";
    }

    @PostMapping("/ask")
    @ResponseBody
    public String ask(@RequestParam String question, Locale locale) {
        if (question == null || question.isBlank()) {
            return "Будь ласка, введіть питання.";
        }
        return aiAssistantService.askAssistant(question.trim(), locale);
    }
}
