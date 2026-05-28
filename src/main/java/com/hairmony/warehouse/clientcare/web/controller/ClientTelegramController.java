package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.config.TelegramConfig;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.ClientRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ClientTelegramController {

    private final ClientRepository clientRepository;
    private final TelegramConfig telegramConfig;

    @GetMapping("/clientcare/clients/{id}/telegram")
    @Transactional
    public String showLinkPage(@PathVariable Long id, Model model) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));

        String token = UUID.randomUUID().toString().replace("-", "");
        client.setTelegramLinkToken(token);
        // dirty checking — no explicit save()

        String link = "https://t.me/" + telegramConfig.getBot().getUsername() + "?start=" + token;

        model.addAttribute("client", client);
        model.addAttribute("telegramLink", link);
        return "clientcare/clients/telegram-link";
    }

    @PostMapping("/clientcare/clients/{id}/telegram/remove")
    @Transactional
    public String removeLink(@PathVariable Long id, RedirectAttributes ra) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));

        client.setTelegramChatId(null);
        client.setTelegramLinkToken(null);
        // dirty checking — no explicit save()

        ra.addFlashAttribute("successMessage", "Telegram відключено");
        return "redirect:/clientcare/clients/" + id;
    }
}
