package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.AppointmentService;
import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.AppointmentDto;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.domain.appointment.AppointmentType;
import com.hairmony.warehouse.service.ClientService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/clientcare/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ClientService clientService;
    private final VisitService visitService;

    @GetMapping
    public String calendarView(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) Long clientId,
                               @RequestParam(required = false) Long linkVisitId,
                               @RequestParam(required = false) String returnTo,
                               Model model) {
        model.addAttribute("initialDate", date != null ? date : LocalDate.now());
        if (clientId != null) {
            model.addAttribute("calClientId", clientId);
            model.addAttribute("calClientName", clientService.findById(clientId).getName());
        }
        if (linkVisitId != null) model.addAttribute("calLinkVisitId", linkVisitId);
        if (returnTo != null)   model.addAttribute("calReturnTo", returnTo);
        return "clientcare/appointments/day";
    }

    /** JSON event feed consumed by FullCalendar. */
    @GetMapping("/api")
    @ResponseBody
    public List<Map<String, Object>> api(@RequestParam String start,
                                         @RequestParam String end) {
        // Take first 19 chars to strip any timezone offset FullCalendar may append
        LocalDateTime from = LocalDateTime.parse(start.substring(0, 19));
        LocalDateTime to   = LocalDateTime.parse(end.substring(0, 19));
        return appointmentService.getAppointmentsBetween(from, to)
                .stream().map(this::toCalendarEvent).toList();
    }

    /** Drag-and-drop / resize reschedule endpoint. */
    @PostMapping("/{id}/reschedule")
    @ResponseBody
    public ResponseEntity<Void> reschedule(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime end) {
        appointmentService.reschedule(id, start, end);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) Long clientId,
                          @RequestParam(required = false) String startTime,
                          @RequestParam(required = false) String returnTo,
                          @RequestParam(required = false) Long linkVisitId,
                          Model model) {
        LocalDate formDate = date != null ? date : LocalDate.now();
        LocalTime lt = parseStartTime(startTime);
        AppointmentDto dto = new AppointmentDto();
        dto.setStartAt(formDate.atTime(lt));
        dto.setEndAt(formDate.atTime(lt.plusHours(1)));
        dto.setStatus(AppointmentStatus.PLANNED);
        if (clientId != null) {
            dto.setClientId(clientId);
            model.addAttribute("clientLocked", true);
            model.addAttribute("lockedClientName", clientService.findById(clientId).getName());
        }
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (linkVisitId != null) model.addAttribute("linkVisitId", linkVisitId);
        populateFormModel(model);
        model.addAttribute("appointment", dto);
        model.addAttribute("formDate", formDate);
        return "clientcare/appointments/form";
    }

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("appointment") AppointmentDto dto,
                       BindingResult result,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate formDate,
                       @RequestParam(required = false) String returnTo,
                       @RequestParam(required = false) Long linkVisitId,
                       Model model) {
        if (result.hasErrors()) {
            populateFormModel(model);
            model.addAttribute("formDate", formDate);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (linkVisitId != null) model.addAttribute("linkVisitId", linkVisitId);
            return "clientcare/appointments/form";
        }
        if (dto.getStartAt() != null && dto.getStartAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            result.rejectValue("startAt", "appointment.pastTime", "Неможливо створити запис у минулому");
            populateFormModel(model);
            model.addAttribute("formDate", formDate);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (linkVisitId != null) model.addAttribute("linkVisitId", linkVisitId);
            return "clientcare/appointments/form";
        }
        Long appointmentId = appointmentService.save(dto);
        if (linkVisitId != null) {
            visitService.linkAppointment(linkVisitId, appointmentId);
        }
        if (returnTo != null && returnTo.matches("^/[^/].*")) {
            return "redirect:" + returnTo;
        }
        return redirectToDay(dto.getStartAt() != null ? dto.getStartAt().toLocalDate() : LocalDate.now());
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @RequestParam(required = false) String returnTo,
                           Model model) {
        AppointmentDto dto = appointmentService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        populateFormModel(model);
        model.addAttribute("appointment", dto);
        model.addAttribute("isEdit", true);
        model.addAttribute("formDate", dto.getStartAt() != null
                ? dto.getStartAt().toLocalDate() : LocalDate.now());
        boolean canComplete = dto.getClientId() != null
                && (dto.getStatus() == AppointmentStatus.PLANNED
                    || dto.getStatus() == AppointmentStatus.CONFIRMED);
        boolean isOverdue = canComplete
                && dto.getStartAt() != null
                && dto.getStartAt().isBefore(LocalDateTime.now());
        boolean isNoShow = dto.getClientId() != null
                && dto.getStatus() == AppointmentStatus.NO_SHOW;
        model.addAttribute("canComplete", canComplete);
        model.addAttribute("isOverdue", isOverdue);
        model.addAttribute("isNoShow", isNoShow);
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/appointments/form";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        appointmentService.changeStatus(id, AppointmentStatus.COMPLETED);
        LocalDate date = dto.getStartAt() != null ? dto.getStartAt().toLocalDate() : LocalDate.now();
        if (dto.getClientId() != null) {
            String returnTo = "/clientcare/appointments?date=" + date;
            return "redirect:/clientcare/visits/new?clientId=" + dto.getClientId()
                    + "&returnTo=" + returnTo;
        }
        return redirectToDay(date);
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("appointment") AppointmentDto dto,
                         BindingResult result,
                         @RequestParam(required = false) String returnTo,
                         Model model) {
        if (result.hasErrors()) {
            populateFormModel(model);
            model.addAttribute("isEdit", true);
            model.addAttribute("formDate", dto.getStartAt() != null
                    ? dto.getStartAt().toLocalDate() : LocalDate.now());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/appointments/form";
        }
        if (dto.getStartAt() != null && dto.getStartAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            result.rejectValue("startAt", "appointment.pastTime", "Неможливо зберегти запис у минулому");
            populateFormModel(model);
            model.addAttribute("isEdit", true);
            model.addAttribute("formDate", dto.getStartAt().toLocalDate());
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/appointments/form";
        }
        appointmentService.update(id, dto);
        if (returnTo != null && returnTo.matches("^/[^/].*")) {
            return "redirect:" + returnTo;
        }
        return redirectToDay(dto.getStartAt() != null ? dto.getStartAt().toLocalDate() : LocalDate.now());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        LocalDate date = appointmentService.findById(id)
                .map(a -> a.getStartAt().toLocalDate())
                .orElse(LocalDate.now());
        appointmentService.delete(id);
        return redirectToDay(date);
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam AppointmentStatus status,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        appointmentService.changeStatus(id, status);
        // NO_SHOW: redirect to edit page so the follow-up action panel is shown
        if (status == AppointmentStatus.NO_SHOW) {
            return "redirect:/clientcare/appointments/" + id + "/edit";
        }
        return redirectToDay(date != null ? date : LocalDate.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void populateFormModel(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("types", AppointmentType.values());
    }

    private String redirectToDay(LocalDate date) {
        return "redirect:/clientcare/appointments?date=" + date;
    }

    private LocalTime parseStartTime(String startTime) {
        if (startTime == null || startTime.isBlank()) return LocalTime.of(9, 0);
        try { return LocalTime.parse(startTime); }
        catch (Exception e) { return LocalTime.of(9, 0); }
    }

    private Map<String, Object> toCalendarEvent(AppointmentDto dto) {
        String  color   = statusColor(dto.getStatus());
        boolean movable = dto.getStatus() == AppointmentStatus.PLANNED
                       || dto.getStatus() == AppointmentStatus.CONFIRMED;

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("id",              String.valueOf(dto.getId()));
        evt.put("title",           dto.getDisplayName());
        evt.put("start",           dto.getStartAt().toString());
        evt.put("end",             dto.getEndAt().toString());
        evt.put("backgroundColor", color);
        evt.put("borderColor",     color);
        evt.put("editable",        movable);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status",   dto.getStatus().name());
        props.put("clientId", dto.getClientId());
        props.put("phone",    dto.getDisplayPhone());
        props.put("editUrl",  "/clientcare/appointments/" + dto.getId() + "/edit");
        evt.put("extendedProps", props);

        return evt;
    }

    private static String statusColor(AppointmentStatus status) {
        return switch (status) {
            case PLANNED   -> "#0d6efd";
            case CONFIRMED -> "#4a7c59";
            case COMPLETED -> "#adb5bd";
            case CANCELLED -> "#dc3545";
            case NO_SHOW   -> "#fd7e14";
        };
    }
}
