package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.AppointmentService;
import com.hairmony.warehouse.clientcare.service.FollowUpService;
import com.hairmony.warehouse.clientcare.service.SalonServiceService;
import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.AppointmentDto;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final SalonServiceService salonServiceService;
    private final FollowUpService followUpService;

    @GetMapping
    public String calendarView(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) Long clientId,
                               @RequestParam(required = false) Long linkVisitId,
                               @RequestParam(required = false) String returnTo,
                               @RequestParam(required = false) Long serviceId,
                               @RequestParam(required = false) Long rebookedFromId,
                               @RequestParam(required = false) Long editApptId,
                               Model model) {
        model.addAttribute("initialDate", date != null ? date : LocalDate.now());
        if (clientId != null) {
            model.addAttribute("calClientId", clientId);
            model.addAttribute("calClientName", clientService.findById(clientId).getName());
        }
        if (linkVisitId    != null) model.addAttribute("calLinkVisitId",    linkVisitId);
        if (returnTo       != null) model.addAttribute("calReturnTo",        returnTo);
        if (serviceId      != null) model.addAttribute("calServiceId",       serviceId);
        if (rebookedFromId != null) model.addAttribute("calRebookedFromId",  rebookedFromId);
        if (editApptId     != null) model.addAttribute("calEditApptId",      editApptId);
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

    /** AJAX: check for the latest unresolved (overdue/no-show) appointment for a client.
     *  Returns 200 + JSON when found, 204 when none. */
    @GetMapping("/api/overdue-check")
    @ResponseBody
    public ResponseEntity<?> overdueCheck(@RequestParam Long clientId) {
        return appointmentService.getLatestOverdueForClient(clientId)
                .filter(a -> a.getStatus() != AppointmentStatus.NO_SHOW)
                .map(a -> {
                    String svcName = "";
                    if (a.getServiceId() != null) {
                        try { svcName = salonServiceService.findById(a.getServiceId()).getName(); }
                        catch (Exception ignored) {}
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("id",          a.getId());
                    resp.put("clientId",    a.getClientId());
                    resp.put("startAt",     a.getStartAt().toString());
                    resp.put("serviceName", svcName);
                    resp.put("status",      a.getStatus().name());
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.noContent().build());
    }

    /** AJAX: quickly resolve an overdue appointment (COMPLETED / NO_SHOW / CANCELLED) without redirect. */
    @PostMapping("/{id}/resolve")
    @ResponseBody
    public ResponseEntity<Void> resolveOverdue(@PathVariable Long id,
                                               @RequestParam AppointmentStatus status) {
        if (status != AppointmentStatus.COMPLETED
                && status != AppointmentStatus.NO_SHOW
                && status != AppointmentStatus.CANCELLED) {
            return ResponseEntity.badRequest().build();
        }
        appointmentService.changeStatus(id, status);
        return ResponseEntity.ok().build();
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
                          @RequestParam(required = false) Long rebookedFromId,
                          @RequestParam(required = false) Long serviceId,
                          @RequestParam(required = false) String notes,
                          Model model) {
        LocalDate formDate = date != null ? date : LocalDate.now();
        LocalTime lt = parseStartTime(startTime);
        AppointmentDto dto = new AppointmentDto();
        dto.setStartAt(formDate.atTime(lt));
        dto.setEndAt(formDate.atTime(lt.plusHours(2)));
        dto.setStatus(AppointmentStatus.PLANNED);
        if (serviceId != null) dto.setServiceId(serviceId);
        if (notes != null && !notes.isBlank()) dto.setNotes(notes);
        if (clientId != null) {
            dto.setClientId(clientId);
            model.addAttribute("clientLocked", true);
            model.addAttribute("lockedClientName", clientService.findById(clientId).getName());
        }
        addOverdueWarning(clientId, rebookedFromId, model);
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        if (linkVisitId != null) model.addAttribute("linkVisitId", linkVisitId);
        addMissedAppointmentBanner(rebookedFromId, model);
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
                       @RequestParam(required = false) Long rebookedFromId,
                       Model model) {
        if (result.hasErrors()) {
            populateFormModel(model);
            model.addAttribute("formDate", formDate);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (linkVisitId != null) model.addAttribute("linkVisitId", linkVisitId);
            addMissedAppointmentBanner(rebookedFromId, model);
            addOverdueWarning(dto.getClientId(), rebookedFromId, model);
            return "clientcare/appointments/form";
        }
        if (dto.getStartAt() != null && dto.getStartAt().isBefore(LocalDateTime.now(ZoneId.of("Europe/Warsaw")).minusMinutes(5))) {
            result.rejectValue("startAt", "appointment.pastTime", "Неможливо створити запис у минулому");
            populateFormModel(model);
            model.addAttribute("formDate", formDate);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            if (linkVisitId != null) model.addAttribute("linkVisitId", linkVisitId);
            addMissedAppointmentBanner(rebookedFromId, model);
            addOverdueWarning(dto.getClientId(), rebookedFromId, model);
            return "clientcare/appointments/form";
        }
        Long appointmentId = appointmentService.save(dto);
        if (linkVisitId != null) {
            visitService.linkAppointment(linkVisitId, appointmentId);
        }
        if (returnTo != null && returnTo.matches("^/[^/].*")) {
            return "redirect:" + returnTo;
        }
        return "redirect:/clientcare/appointments/" + appointmentId + "/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @RequestParam(required = false) String returnTo,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                           @RequestParam(required = false) String startTime,
                           Model model) {
        AppointmentDto dto = appointmentService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        // When returning from calendar slot pick, override date/time preserving original duration
        if (startDate != null) {
            LocalTime lt = parseStartTime(startTime);
            LocalDateTime newStart = startDate.atTime(lt);
            LocalDateTime newEnd;
            if (dto.getStartAt() != null && dto.getEndAt() != null) {
                long durationMinutes = Duration.between(dto.getStartAt(), dto.getEndAt()).toMinutes();
                newEnd = newStart.plusMinutes(durationMinutes > 0 ? durationMinutes : 120);
            } else {
                newEnd = newStart.plusHours(2);
            }
            dto.setStartAt(newStart);
            dto.setEndAt(newEnd);
            model.addAttribute("slotPicked", true);
        }
        populateFormModel(model);
        model.addAttribute("appointment", dto);
        model.addAttribute("isEdit", true);
        model.addAttribute("formDate", dto.getStartAt() != null
                ? dto.getStartAt().toLocalDate() : LocalDate.now());
        boolean canComplete = dto.getClientId() != null
                && (dto.getStatus() == AppointmentStatus.PLANNED
                    || dto.getStatus() == AppointmentStatus.CONFIRMED);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Warsaw"));
        boolean isInProgress = canComplete
                && dto.getStartAt() != null && dto.getEndAt() != null
                && dto.getStartAt().isBefore(now) && dto.getEndAt().isAfter(now);
        boolean isOverdue = canComplete
                && dto.getEndAt() != null
                && dto.getEndAt().isBefore(now);
        boolean isNoShow = dto.getClientId() != null
                && dto.getStatus() == AppointmentStatus.NO_SHOW;
        model.addAttribute("canComplete", canComplete);
        model.addAttribute("isInProgress", isInProgress);
        model.addAttribute("isOverdue", isOverdue);
        model.addAttribute("isNoShow", isNoShow);
        if (isNoShow) {
            model.addAttribute("followupAdded", followUpService.hasActivity(dto.getClientId()));
        }
        lockClientForEdit(dto, model);
        if (returnTo != null) model.addAttribute("returnTo", returnTo);
        return "clientcare/appointments/form";
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        // Status change happens only after the visit is saved (completeAppointmentId param).
        // This prevents the appointment from being stuck as COMPLETED when the user cancels the visit form.
        LocalDate date = dto.getStartAt() != null ? dto.getStartAt().toLocalDate() : LocalDate.now();
        if (dto.getClientId() != null) {
            String returnTo = "/clientcare/appointments?date=" + date;
            return "redirect:/clientcare/visits/new?clientId=" + dto.getClientId()
                    + "&completeAppointmentId=" + id
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
            lockClientForEdit(dto, model);
            if (returnTo != null) model.addAttribute("returnTo", returnTo);
            return "clientcare/appointments/form";
        }
        if (dto.getStartAt() != null && dto.getStartAt().isBefore(LocalDateTime.now(ZoneId.of("Europe/Warsaw")).minusMinutes(5))) {
            result.rejectValue("startAt", "appointment.pastTime", "Неможливо зберегти запис у минулому");
            populateFormModel(model);
            model.addAttribute("isEdit", true);
            model.addAttribute("formDate", dto.getStartAt().toLocalDate());
            lockClientForEdit(dto, model);
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

    @PostMapping("/{id}/noshow-followup")
    public String noshowFollowup(@PathVariable Long id) {
        AppointmentDto dto = appointmentService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        if (dto.getClientId() != null) {
            String dateStr = dto.getStartAt() != null
                    ? dto.getStartAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'о' HH:mm"))
                    : "";
            followUpService.note(dto.getClientId(), "Не прийшов на запис " + dateStr);
        }
        return "redirect:/clientcare/followups";
    }

    @PostMapping("/{id}/cancel")
    public String cancelWithReason(@PathVariable Long id,
                                   @RequestParam(required = false) String reason,
                                   @RequestParam(defaultValue = "false") boolean addToFollowup,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        AppointmentDto dto = appointmentService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        appointmentService.changeStatus(id, AppointmentStatus.CANCELLED);
        if (addToFollowup && dto.getClientId() != null) {
            String dateStr = dto.getStartAt() != null
                    ? dto.getStartAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'о' HH:mm"))
                    : "";
            String note = "Скасував(ла) запис " + dateStr;
            if (reason != null && !reason.isBlank()) {
                note += ": " + reason.trim();
            }
            followUpService.note(dto.getClientId(), note);
        }
        return redirectToDay(date != null ? date : LocalDate.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void populateFormModel(Model model) {
        model.addAttribute("clients", clientService.findAll());
        model.addAttribute("statuses", AppointmentStatus.values());
        model.addAttribute("services", salonServiceService.findAllActive());
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
        boolean overdue = (dto.getStatus() == AppointmentStatus.PLANNED
                        || dto.getStatus() == AppointmentStatus.CONFIRMED)
                       && dto.getStartAt().isBefore(LocalDateTime.now(ZoneId.of("Europe/Warsaw")));
        boolean movable = !overdue
                       && (dto.getStatus() == AppointmentStatus.PLANNED
                        || dto.getStatus() == AppointmentStatus.CONFIRMED);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("id",       String.valueOf(dto.getId()));
        evt.put("title",    dto.getDisplayName());
        evt.put("start",    dto.getStartAt().toString());
        evt.put("end",      dto.getEndAt().toString());
        evt.put("editable", movable);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status",   dto.getStatus().name());
        props.put("clientId", dto.getClientId());
        props.put("phone",    dto.getDisplayPhone());
        props.put("editUrl",  "/clientcare/appointments/" + dto.getId() + "/edit");
        evt.put("extendedProps", props);

        return evt;
    }

    /** Locks the client field in edit mode — no toggle or dropdown shown. */
    private void lockClientForEdit(AppointmentDto dto, Model model) {
        model.addAttribute("clientLocked", true);
        if (dto.getClientId() != null) {
            String name = dto.getClientName() != null
                    ? dto.getClientName()
                    : clientService.findById(dto.getClientId()).getName();
            model.addAttribute("lockedClientName", name);
        } else {
            String display = dto.getGuestName() != null ? dto.getGuestName() : "";
            if (dto.getGuestPhone() != null && !dto.getGuestPhone().isBlank()) {
                display += " · " + dto.getGuestPhone();
            }
            model.addAttribute("lockedClientName", display);
        }
    }

    /** Checks for the latest unresolved appointment for a client and adds it to the model for the warning banner. */
    private void addOverdueWarning(Long clientId, Model model) {
        addOverdueWarning(clientId, null, model);
    }

    private void addOverdueWarning(Long clientId, Long excludeId, Model model) {
        if (clientId == null) return;
        appointmentService.getLatestOverdueForClient(clientId)
                .filter(a -> a.getStatus() != AppointmentStatus.NO_SHOW)
                .filter(a -> excludeId == null || !excludeId.equals(a.getId()))
                .ifPresent(a -> {
                    if (a.getServiceId() != null) {
                        try { a.setServiceName(salonServiceService.findById(a.getServiceId()).getName()); }
                        catch (Exception ignored) {}
                    }
                    model.addAttribute("overdueAppointment", a);
                });
    }

    /** Loads the missed (NO_SHOW) appointment's startAt for the rebook banner. */
    private void addMissedAppointmentBanner(Long rebookedFromId, Model model) {
        if (rebookedFromId == null) return;
        model.addAttribute("rebookedFromId", rebookedFromId);
        appointmentService.findById(rebookedFromId)
                .filter(a -> a.getStartAt() != null
                        && (a.getStatus() == AppointmentStatus.PLANNED
                            || a.getStatus() == AppointmentStatus.CONFIRMED))
                .ifPresent(a -> model.addAttribute("missedAppointmentAt", a.getStartAt()));
    }
}
