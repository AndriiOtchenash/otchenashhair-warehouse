package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.GoogleDriveService;
import com.hairmony.warehouse.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DrivePhotoController {

    private final ClientService clientService;
    private final GoogleDriveService googleDriveService;

    @GetMapping("/clientcare/clients/{clientId}/drive-photos")
    public String drivePhotos(@PathVariable Long clientId,
                              @RequestParam(required = false) String returnTo,
                              Model model) {
        var client = clientService.findById(clientId);
        model.addAttribute("client", client);
        model.addAttribute("backUrl",
                returnTo != null ? returnTo : "/clientcare/clients/" + clientId);
        model.addAttribute("noFolder", false);
        model.addAttribute("driveError", false);
        model.addAttribute("drivePhotos", List.of());

        // Resolve folder ID: per-client override → auto-lookup by name in root folder
        String folderId = client.getDriveFolderId();

        if (folderId == null || folderId.isBlank()) {
            if (!googleDriveService.isRootFolderConfigured()) {
                model.addAttribute("noFolder", true);
                return "clientcare/clients/drive-photos";
            }
            try {
                folderId = googleDriveService.findSubfolderIdByName(client.getName());
            } catch (GoogleDriveService.DriveAccessException e) {
                model.addAttribute("driveError", true);
                return "clientcare/clients/drive-photos";
            }
            if (folderId == null) {
                model.addAttribute("noFolder", true);
                return "clientcare/clients/drive-photos";
            }
        }

        try {
            var photos = googleDriveService.listImagesInFolder(folderId);
            model.addAttribute("drivePhotos", photos);
        } catch (GoogleDriveService.DriveAccessException e) {
            model.addAttribute("driveError", true);
        }

        return "clientcare/clients/drive-photos";
    }
}
