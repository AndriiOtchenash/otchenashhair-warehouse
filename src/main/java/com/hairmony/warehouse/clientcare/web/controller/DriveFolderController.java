package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.web.dto.DriveFolderDto;
import com.hairmony.warehouse.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class DriveFolderController {

    private final ClientService clientService;

    @GetMapping("/clientcare/clients/{id}/drive-folder/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var client = clientService.findById(id);
        DriveFolderDto dto = new DriveFolderDto();
        dto.setClientId(id);
        dto.setDriveFolderUrl(client.getDriveFolderUrl());
        model.addAttribute("driveFolder", dto);
        model.addAttribute("client", client);
        return "clientcare/clients/drive-folder-form";
    }

    @PostMapping("/clientcare/clients/{id}/drive-folder/remove")
    public String remove(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        clientService.removeDriveFolder(id);
        redirectAttributes.addFlashAttribute("successMessage", "drive.folder.success.removed");
        return "redirect:/clientcare/clients/" + id;
    }

    @PostMapping("/clientcare/clients/{id}/drive-folder")
    public String save(@PathVariable Long id,
                       @Valid @ModelAttribute("driveFolder") DriveFolderDto dto,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(id));
            return "clientcare/clients/drive-folder-form";
        }
        clientService.updateDriveFolder(id, dto.getDriveFolderUrl());
        redirectAttributes.addFlashAttribute("successMessage", "drive.folder.success.saved");
        return "redirect:/clientcare/clients/" + id;
    }
}
