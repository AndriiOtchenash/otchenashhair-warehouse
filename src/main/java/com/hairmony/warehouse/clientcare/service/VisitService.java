package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.PaymentMethod;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
import com.hairmony.warehouse.domain.visit.Visit;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.GiftCertificateRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;
    private final ClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;
    private final GiftCertificateRepository certificateRepository;

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
        applyCertificatePayment(dto);
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> new IllegalStateException("client.notFound"));
        Visit visit = Visit.builder()
                .client(client)
                .visitDate(dto.getVisitDate())
                .complaint(dto.getComplaint())
                .scalpCondition(dto.getScalpCondition())
                .recommendations(dto.getRecommendations())
                .notes(dto.getNotes())
                .serviceId(dto.getServiceId())
                .priceAtTime(dto.getPriceAtTime())
                .paymentMethod(dto.getPaymentMethod())
                .paid(dto.isPaid())
                .certificateCode(trimOrNull(dto.getCertificateCode()))
                .build();
        return visitRepository.save(visit).getId();
    }

    @Transactional
    public void update(Long id, VisitDto dto) {
        Visit visit = visitRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("visit.notFound"));

        // If the same certificate code is already applied to this visit — skip re-redemption.
        // The cert was redeemed on first save; editing the visit protocol should not touch it again.
        String existingCertCode = visit.getCertificateCode();
        String incomingCertCode = trimOrNull(dto.getCertificateCode());
        boolean sameCertAlreadyApplied = dto.getPaymentMethod() == PaymentMethod.CERTIFICATE
                && incomingCertCode != null
                && incomingCertCode.equals(existingCertCode);

        if (sameCertAlreadyApplied) {
            dto.setPaid(true); // keep paid=true, cert stays REDEEMED
        } else {
            applyCertificatePayment(dto); // new cert code or payment method change
        }

        visit.setVisitDate(dto.getVisitDate());
        visit.setComplaint(dto.getComplaint());
        visit.setScalpCondition(dto.getScalpCondition());
        visit.setRecommendations(dto.getRecommendations());
        visit.setNotes(dto.getNotes());
        visit.setServiceId(dto.getServiceId());
        visit.setPriceAtTime(dto.getPriceAtTime());
        visit.setPaymentMethod(dto.getPaymentMethod());
        visit.setPaid(dto.isPaid());
        visit.setCertificateCode(incomingCertCode);
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

    @Transactional(readOnly = true)
    public java.util.Set<Long> getClientIdsWithUnpaidVisits() {
        return visitRepository.findClientIdsWithUnpaidVisits();
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
        dto.setServiceId(v.getServiceId());
        dto.setPriceAtTime(v.getPriceAtTime());
        dto.setPaymentMethod(v.getPaymentMethod());
        dto.setPaid(v.isPaid());
        dto.setCertificateCode(v.getCertificateCode());
        if (v.getNextAppointment() != null) {
            dto.setNextAppointmentId(v.getNextAppointment().getId());
            dto.setNextAppointmentStartAt(v.getNextAppointment().getStartAt());
        }
        return dto;
    }

    private static final Set<PaymentMethod> AUTO_PAID_METHODS =
            Set.of(PaymentMethod.BARTER, PaymentMethod.COMPLIMENTARY, PaymentMethod.PROMO);

    /**
     * Auto-marks non-cash visits as paid:
     * - CERTIFICATE: validates code, marks cert REDEEMED
     * - BARTER/COMPLIMENTARY/PROMO: paid=true, priceAtTime cleared (no cash exchanged)
     */
    private void applyCertificatePayment(VisitDto dto) {
        if (AUTO_PAID_METHODS.contains(dto.getPaymentMethod())) {
            dto.setPaid(true);
            dto.setPriceAtTime(null);
            return;
        }
        if (dto.getPaymentMethod() != PaymentMethod.CERTIFICATE) return;
        String code = trimOrNull(dto.getCertificateCode());
        if (code == null) return;

        GiftCertificate cert = certificateRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException(
                        "visit.certificate.notFound:" + code));

        if (cert.getStatus() == GiftCertificateStatus.EXPIRED) {
            throw new IllegalStateException("visit.certificate.expired:" + code);
        }
        if (cert.getStatus() != GiftCertificateStatus.ACTIVE) {
            throw new IllegalStateException("visit.certificate.notActive:" + code);
        }

        cert.setStatus(GiftCertificateStatus.REDEEMED);
        cert.setRedeemedAt(LocalDateTime.now());
        dto.setPaid(true);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
