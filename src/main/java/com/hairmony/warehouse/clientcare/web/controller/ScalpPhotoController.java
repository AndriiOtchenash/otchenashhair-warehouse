package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.ScalpPhotoService;
import com.hairmony.warehouse.clientcare.web.dto.ScalpPhotoDto;
import com.hairmony.warehouse.domain.scalp.ScalpPhoto;
import com.hairmony.warehouse.domain.scalp.ScalpZone;
import com.hairmony.warehouse.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ScalpPhotoController {

    private final ScalpPhotoService scalpPhotoService;
    private final ClientService clientService;

    @GetMapping("/clientcare/photos/new")
    public String form(@RequestParam Long clientId, Model model) {
        ScalpPhotoDto dto = new ScalpPhotoDto();
        dto.setClientId(clientId);
        dto.setTakenAt(LocalDate.now());
        model.addAttribute("photo", dto);
        model.addAttribute("client", clientService.findById(clientId));
        model.addAttribute("zones", ScalpZone.values());
        return "clientcare/photos/form";
    }

    @PostMapping("/clientcare/photos/new")
    public String save(@Valid @ModelAttribute("photo") ScalpPhotoDto dto,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("zones", ScalpZone.values());
            return "clientcare/photos/form";
        }
        scalpPhotoService.save(dto);
        redirectAttributes.addFlashAttribute("successMessage", "scalp.photo.success.added");
        return "redirect:/clientcare/clients/" + dto.getClientId();
    }

    @GetMapping("/clientcare/photos/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @RequestParam(required = false) String returnTo,
                           Model model) {
        ScalpPhotoDto dto = scalpPhotoService.findById(id);
        model.addAttribute("photo", dto);
        model.addAttribute("client", clientService.findById(dto.getClientId()));
        model.addAttribute("zones", ScalpZone.values());
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/photos/form";
    }

    @PostMapping("/clientcare/photos/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("photo") ScalpPhotoDto dto,
                         BindingResult result,
                         @RequestParam(required = false) String returnTo,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("client", clientService.findById(dto.getClientId()));
            model.addAttribute("zones", ScalpZone.values());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/photos/form";
        }
        scalpPhotoService.update(id, dto);
        redirectAttributes.addFlashAttribute("successMessage", "scalp.photo.success.updated");
        if ("gallery".equals(returnTo)) {
            return "redirect:/clientcare/clients/" + dto.getClientId() + "/photos";
        }
        return "redirect:/clientcare/clients/" + dto.getClientId();
    }

    @PostMapping("/clientcare/photos/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam Long clientId,
                         @RequestParam(required = false) String returnTo) {
        scalpPhotoService.delete(id, clientId);
        if ("gallery".equals(returnTo)) {
            return "redirect:/clientcare/clients/" + clientId + "/photos";
        }
        return "redirect:/clientcare/clients/" + clientId;
    }

    @GetMapping("/clientcare/clients/{clientId}/photos")
    public String gallery(@PathVariable Long clientId,
                          @RequestParam(required = false) String zone,
                          @RequestParam(required = false) String returnTo,
                          Model model) {
        List<ScalpPhoto> allPhotos = scalpPhotoService.findByClientId(clientId);

        ScalpZone activeZone = null;
        if (zone != null && !zone.isEmpty()) {
            try {
                activeZone = ScalpZone.valueOf(zone);
            } catch (IllegalArgumentException ignored) {}
        }

        final ScalpZone filterZone = activeZone;
        List<ScalpPhoto> photos = filterZone != null
                ? allPhotos.stream().filter(p -> p.getZone() == filterZone).toList()
                : allPhotos;

        Map<ScalpZone, Long> zoneCounts = allPhotos.stream()
                .collect(Collectors.groupingBy(ScalpPhoto::getZone, Collectors.counting()));

        model.addAttribute("client", clientService.findById(clientId));
        model.addAttribute("photos", photos);
        model.addAttribute("totalCount", allPhotos.size());
        model.addAttribute("zones", ScalpZone.values());
        model.addAttribute("activeZone", zone);
        model.addAttribute("zoneCounts", zoneCounts);
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/clients/photos";
    }
}
