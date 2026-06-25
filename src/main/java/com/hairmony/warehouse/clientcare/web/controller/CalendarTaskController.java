package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.CalendarTaskService;
import com.hairmony.warehouse.clientcare.web.dto.CalendarTaskDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/clientcare/calendar-tasks")
public class CalendarTaskController {

    private final CalendarTaskService calendarTaskService;

    @GetMapping("/api")
    @ResponseBody
    public List<CalendarTaskDto> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return calendarTaskService.findByDate(date);
    }

    @GetMapping("/api/counts")
    @ResponseBody
    public Map<String, Long> getCounts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return calendarTaskService.getCountsByDateRange(start, end)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue
                ));
    }

    @PostMapping("/api")
    @ResponseBody
    public ResponseEntity<CalendarTaskDto> create(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taskDate,
            @RequestParam String text,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long appointmentId) {
        if (text == null || text.isBlank() || text.length() > 500) {
            return ResponseEntity.badRequest().build();
        }
        CalendarTaskDto dto = calendarTaskService.save(taskDate, text.trim(), clientId, appointmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/api/{id}/update")
    @ResponseBody
    public ResponseEntity<CalendarTaskDto> update(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate taskDate,
            @RequestParam String text) {
        if (text == null || text.isBlank() || text.length() > 500) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(calendarTaskService.update(id, taskDate, text.trim()));
    }

    @PostMapping("/api/{id}/done")
    @ResponseBody
    public ResponseEntity<Void> toggleDone(@PathVariable Long id) {
        calendarTaskService.toggleDone(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/{id}/delete")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        calendarTaskService.delete(id);
        return ResponseEntity.ok().build();
    }
}
