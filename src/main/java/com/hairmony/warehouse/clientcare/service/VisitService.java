package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.visit.Visit;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;
    private final ClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public List<VisitDto> findByClientId(Long clientId) {
        return visitRepository.findAllByClientIdOrderByVisitDateDescCreatedAtDesc(clientId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public VisitDto findById(Long id) {
        return toDto(visitRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("visit.notFound")));
    }

    @Transactional(readOnly = true)
    public boolean isLatestVisit(Long visitId, Long clientId) {
        return visitRepository.findFirstByClientIdOrderByVisitDateDescCreatedAtDesc(clientId)
                .map(v -> v.getId().equals(visitId))
                .orElse(false);
    }

    /** Saves a new visit and returns its generated ID. */
    @Transactional
    public Long save(VisitDto dto) {
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> new IllegalStateException("client.notFound"));
        Visit visit = Visit.builder()
                .client(client)
                .visitDate(dto.getVisitDate())
                .complaint(dto.getComplaint())
                .scalpCondition(dto.getScalpCondition())
                .recommendations(dto.getRecommendations())
                .notes(dto.getNotes())
                .build();
        return visitRepository.save(visit).getId();
    }

    @Transactional
    public void update(Long id, VisitDto dto) {
        Visit visit = visitRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("visit.notFound"));
        visit.setVisitDate(dto.getVisitDate());
        visit.setComplaint(dto.getComplaint());
        visit.setScalpCondition(dto.getScalpCondition());
        visit.setRecommendations(dto.getRecommendations());
        visit.setNotes(dto.getNotes());
        // nextAppointment is managed separately via linkAppointment() — do not clear here
    }

    /** Links an appointment to a visit as the planned next appointment. */
    @Transactional
    public void linkAppointment(Long visitId, Long appointmentId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new IllegalStateException("visit.notFound"));
        Appointment appointment = appointmentRepository.getReferenceById(appointmentId);
        visit.setNextAppointment(appointment);
    }

    /** Clears nextAppointment link from whichever visit points to this appointment (called on completion). */
    @Transactional
    public void unlinkCompletedAppointment(Long appointmentId) {
        visitRepository.findByNextAppointmentId(appointmentId)
                .ifPresent(v -> v.setNextAppointment(null));
    }

    @Transactional
    public void delete(Long id) {
        visitRepository.deleteById(id);
    }

    private VisitDto toDto(Visit v) {
        VisitDto dto = new VisitDto();
        dto.setId(v.getId());
        dto.setClientId(v.getClient().getId());
        dto.setVisitDate(v.getVisitDate());
        dto.setComplaint(v.getComplaint());
        dto.setScalpCondition(v.getScalpCondition());
        dto.setRecommendations(v.getRecommendations());
        dto.setNotes(v.getNotes());
        dto.setCreatedAt(v.getCreatedAt());
        if (v.getNextAppointment() != null) {
            dto.setNextAppointmentId(v.getNextAppointment().getId());
            dto.setNextAppointmentStartAt(v.getNextAppointment().getStartAt());
        }
        return dto;
    }
}
