package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.AppointmentDto;
import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AppointmentService {

    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<AppointmentDto> getDayAppointments(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.plusDays(1).atStartOfDay();
        return appointmentRepository
                .findAllByStartAtBetweenOrderByStartAtAsc(from, to)
                .stream().map(this::toDto).toList();
    }

    public Long save(AppointmentDto dto) {
        // Auto-promote guest to a real client so they appear in the follow-up queue
        if (dto.getClientId() == null && trimOrNull(dto.getGuestName()) != null) {
            LocalDate apptDate = dto.getStartAt() != null
                    ? dto.getStartAt().toLocalDate() : LocalDate.now();
            String note = apptDate.format(NOTE_DATE) + " — запис нового клієнта";
            Client newClient = clientRepository.save(
                    Client.builder()
                            .name(dto.getGuestName().trim())
                            .phone(trimOrNull(dto.getGuestPhone()))
                            .notes(note)
                            .build());
            dto.setClientId(newClient.getId());
            // guestName/guestPhone will be set to null by toEntity() since clientId is now set
        }
        return appointmentRepository.save(toEntity(dto)).getId();
    }

    @Transactional(readOnly = true)
    public Optional<AppointmentDto> findById(Long id) {
        return appointmentRepository.findById(id).map(this::toDto);
    }

    public void update(Long id, AppointmentDto dto) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        a.setClient(resolveClient(dto));
        a.setStartAt(dto.getStartAt());
        a.setEndAt(dto.getEndAt());
        a.setStatus(dto.getStatus() != null ? dto.getStatus() : AppointmentStatus.PLANNED);
        a.setServiceId(dto.getServiceId());
        a.setNotes(trimOrNull(dto.getNotes()));
        // updatedAt handled by @PreUpdate
    }

    /** Returns a map of date → first active appointment for that client on that date. */
    @Transactional(readOnly = true)
    public Map<LocalDate, AppointmentDto> getAppointmentsByDateForClient(Long clientId) {
        return appointmentRepository.findAllByClientIdOrderByStartAtAsc(clientId)
                .stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .collect(Collectors.toMap(
                        a -> a.getStartAt().toLocalDate(),
                        this::toDto,
                        (first, second) -> first   // keep earliest if multiple on same date
                ));
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> getAppointmentsBetween(LocalDateTime start, LocalDateTime end) {
        return appointmentRepository.findAllByStartAtBetweenOrderByStartAtAsc(start, end)
                .stream().map(this::toDto).toList();
    }

    // ── List-page indicators ─────────────────────────────────────────────────

    private static final Collection<AppointmentStatus> ACTIVE_STATUSES =
            EnumSet.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED);

    /** Client IDs with any future PLANNED/CONFIRMED appointment (no upper bound, for list icons). */
    @Transactional(readOnly = true)
    public Set<Long> getClientIdsWithUpcomingAppointments() {
        return appointmentRepository.findClientIdsWithAnyUpcomingFrom(LocalDateTime.now(), ACTIVE_STATUSES);
    }

    /** Client IDs with a PLANNED/CONFIRMED appointment that is past (overdue, for list icons). */
    @Transactional(readOnly = true)
    public Set<Long> getClientIdsWithOverdueAppointments() {
        return appointmentRepository.findClientIdsWithOverdueBefore(LocalDateTime.now(), ACTIVE_STATUSES);
    }

    // ── Detail-page indicators ───────────────────────────────────────────────

    /** Next upcoming PLANNED/CONFIRMED appointment for a client, or empty. */
    @Transactional(readOnly = true)
    public Optional<AppointmentDto> getNextUpcomingForClient(Long clientId) {
        return appointmentRepository
                .findUpcomingByClientId(clientId, LocalDateTime.now(), ACTIVE_STATUSES)
                .stream().findFirst().map(this::toDto);
    }

    /** Most-recent overdue PLANNED/CONFIRMED appointment for a client, or empty. */
    @Transactional(readOnly = true)
    public Optional<AppointmentDto> getLatestOverdueForClient(Long clientId) {
        return appointmentRepository
                .findOverdueByClientId(clientId, LocalDateTime.now(), ACTIVE_STATUSES)
                .stream().findFirst().map(this::toDto);
    }

    public void reschedule(Long id, LocalDateTime newStart, LocalDateTime newEnd) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        if (a.getStatus() != AppointmentStatus.PLANNED && a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot reschedule appointment in status: " + a.getStatus());
        }
        if (newStart.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new IllegalStateException("Cannot reschedule appointment to a past time");
        }
        a.setStartAt(newStart);
        a.setEndAt(newEnd);
    }

    public void delete(Long id) {
        appointmentRepository.deleteById(id);
    }

    public void changeStatus(Long id, AppointmentStatus status) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appointment not found: " + id));
        a.setStatus(status);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AppointmentDto toDto(Appointment a) {
        Client c = a.getClient();
        return AppointmentDto.builder()
                .id(a.getId())
                .clientId(c != null ? c.getId() : null)
                .clientName(c != null ? c.getName() : null)
                .clientPhone(c != null ? c.getPhone() : null)
                .startAt(a.getStartAt())
                .endAt(a.getEndAt())
                .status(a.getStatus())
                .serviceId(a.getServiceId())
                .notes(a.getNotes())
                .build();
    }

    private Appointment toEntity(AppointmentDto dto) {
        return Appointment.builder()
                .client(resolveClient(dto))
                .startAt(dto.getStartAt())
                .endAt(dto.getEndAt())
                .status(dto.getStatus() != null ? dto.getStatus() : AppointmentStatus.PLANNED)
                .serviceId(dto.getServiceId())
                .notes(trimOrNull(dto.getNotes()))
                .build();
    }

    private Client resolveClient(AppointmentDto dto) {
        return dto.getClientId() != null
                ? clientRepository.getReferenceById(dto.getClientId())
                : null;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
