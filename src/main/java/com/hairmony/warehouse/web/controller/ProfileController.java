package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.UserService;
import com.hairmony.warehouse.web.dto.ChangePasswordDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    @GetMapping("/change-password")
    public String changePasswordForm(Model model) {
        model.addAttribute("dto", new ChangePasswordDto());
        return "profile/change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @ModelAttribute("dto") ChangePasswordDto dto,
            BindingResult result,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (result.hasErrors()) {
            return "profile/change-password";
        }

        boolean success = userService.changePassword(userDetails.getUsername(), dto);

        if (!success) {
            model.addAttribute("error", "Невірний поточний пароль або паролі не співпадають");
            return "profile/change-password";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Пароль успішно змінено");
        return "redirect:/";
    }
}
