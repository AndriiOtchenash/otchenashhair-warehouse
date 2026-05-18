package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.FollowUpService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@Validated
public class FollowUpActionController {

    private final FollowUpService followUpService;

    private static final String DEFAULT_QUEUE = "/clientcare/followups";

    @PostMapping("/clientcare/followups/{clientId}/done")
    public String done(@PathVariable Long clientId,
                       @RequestParam(required = false) @Size(max = 500) String note,
                       @RequestParam(defaultValue = DEFAULT_QUEUE) String returnTo) {
        followUpService.done(clientId, note);
        return "redirect:" + safeRedirect(returnTo, DEFAULT_QUEUE);
    }

    @PostMapping("/clientcare/followups/{clientId}/snooze")
    public String snooze(@PathVariable Long clientId,
                         @RequestParam @Min(1) @Max(365) int days,
                         @RequestParam(required = false) @Size(max = 500) String note,
                         @RequestParam(defaultValue = DEFAULT_QUEUE) String returnTo) {
        followUpService.snooze(clientId, days, note);
        return "redirect:" + safeRedirect(returnTo, DEFAULT_QUEUE);
    }

    @PostMapping("/clientcare/followups/{clientId}/note")
    public String note(@PathVariable Long clientId,
                       @RequestParam @Size(max = 500) String text,
                       @RequestParam(defaultValue = DEFAULT_QUEUE) String returnTo) {
        if (text != null && !text.isBlank()) {
            followUpService.note(clientId, text.trim());
        }
        return "redirect:" + safeRedirect(returnTo, DEFAULT_QUEUE);
    }

    @PostMapping("/clientcare/followups/{clientId}/reset")
    public String reset(@PathVariable Long clientId,
                        @RequestParam(defaultValue = DEFAULT_QUEUE) String returnTo) {
        followUpService.reset(clientId);
        return "redirect:" + safeRedirect(returnTo, DEFAULT_QUEUE);
    }

    @PostMapping("/clientcare/followups/{clientId}/returnToQueue")
    public String returnToQueue(@PathVariable Long clientId,
                                @RequestParam(defaultValue = DEFAULT_QUEUE) String returnTo) {
        followUpService.returnToQueue(clientId);
        return "redirect:" + safeRedirect(returnTo, DEFAULT_QUEUE);
    }

    /** AJAX: returns activity log fragment for a client */
    @GetMapping("/clientcare/followups/{clientId}/activity")
    public String activity(@PathVariable Long clientId, Model model) {
        model.addAttribute("activities", followUpService.getActivityForClient(clientId));
        model.addAttribute("clientId", clientId);
        return "clientcare/fragments/followup-activity :: activity";
    }

    /** AJAX: delete any activity record */
    @PostMapping("/clientcare/followups/notes/{id}/delete")
    @ResponseBody
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        followUpService.deleteActivity(id);
        return ResponseEntity.ok().build();
    }

    /** AJAX: delete all activity records for a client */
    @PostMapping("/clientcare/followups/{clientId}/activity/clear")
    @ResponseBody
    public ResponseEntity<Void> clearActivity(@PathVariable Long clientId) {
        followUpService.deleteAllActivity(clientId);
        return ResponseEntity.ok().build();
    }

    /**
     * Validates a returnTo redirect target.
     * Must start with "/" and NOT be a protocol-relative URL (//host).
     * Prevents open redirect attacks.
     */
    private String safeRedirect(String returnTo, String fallback) {
        if (returnTo == null || !returnTo.matches("^/[^/].*")) {
            return fallback;
        }
        return returnTo;
    }
}
