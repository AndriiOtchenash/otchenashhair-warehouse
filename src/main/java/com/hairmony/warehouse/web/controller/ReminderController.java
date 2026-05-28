package com.hairmony.warehouse.web.controller;

import com.hairmony.warehouse.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ReminderController {

    @Value("${internal.secret}")
    private String internalSecret;

    private final ReminderService reminderService;

    @GetMapping("/internal/reminders")
    public ResponseEntity<String> triggerReminders(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        if (!internalSecret.equals(token)) {
            log.warn("ReminderController: unauthorized attempt");
            return ResponseEntity.status(403).body("Forbidden");
        }

        log.info("ReminderController: manual trigger");
        String result = reminderService.sendReminders();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(result);
    }
}
