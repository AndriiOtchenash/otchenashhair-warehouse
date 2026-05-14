package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.FollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class FollowUpActionController {

    private final FollowUpService followUpService;

    @PostMapping("/clientcare/followups/{clientId}/done")
    public String done(@PathVariable Long clientId,
                       @RequestParam(required = false) String note,
                       @RequestParam(defaultValue = "/clientcare/followups") String returnTo) {
        followUpService.done(clientId, note);
        return "redirect:" + returnTo;
    }

    @PostMapping("/clientcare/followups/{clientId}/snooze")
    public String snooze(@PathVariable Long clientId,
                         @RequestParam int days,
                         @RequestParam(required = false) String note,
                         @RequestParam(defaultValue = "/clientcare/followups") String returnTo) {
        followUpService.snooze(clientId, days, note);
        return "redirect:" + returnTo;
    }

    @PostMapping("/clientcare/followups/{clientId}/note")
    public String note(@PathVariable Long clientId,
                       @RequestParam String text,
                       @RequestParam(defaultValue = "/clientcare/followups") String returnTo) {
        if (text != null && !text.isBlank()) {
            followUpService.note(clientId, text.trim());
        }
        return "redirect:" + returnTo;
    }

    @PostMapping("/clientcare/followups/{clientId}/reset")
    public String reset(@PathVariable Long clientId,
                        @RequestParam(defaultValue = "/clientcare/followups") String returnTo) {
        followUpService.reset(clientId);
        return "redirect:" + returnTo;
    }

    @PostMapping("/clientcare/followups/{clientId}/returnToQueue")
    public String returnToQueue(@PathVariable Long clientId,
                                @RequestParam(defaultValue = "/clientcare/followups") String returnTo) {
        followUpService.returnToQueue(clientId);
        return "redirect:" + returnTo;
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
}
