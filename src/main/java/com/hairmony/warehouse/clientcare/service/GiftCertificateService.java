package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.GiftCertificateFormDto;
import com.hairmony.warehouse.clientcare.web.dto.SalonServiceDto;
import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
import com.hairmony.warehouse.repository.GiftCertificateRepository;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GiftCertificateService {

    private final GiftCertificateRepository repository;
    private final ClientService clientService;
    private final SalonServiceService salonServiceService;

    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ── Issue ─────────────────────────────────────────────────────────────────

    @Transactional
    public GiftCertificate issue(GiftCertificateFormDto form) {
        GiftCertificate cert = new GiftCertificate();
        cert.setCode(generateCode());
        cert.setIssuedAt(LocalDateTime.now());
        cert.setExpiresAt(form.getExpiresAt());
        cert.setStatus(GiftCertificateStatus.ACTIVE);
        cert.setNotes(form.getNotes());
        cert.setPrice(form.getPrice() != null ? form.getPrice() : java.math.BigDecimal.ZERO);

        // Resolve recipient display name first — needed for purchaser auto-create note
        String recipientDisplay = form.getRecipientClientId() != null
                ? clientService.findById(form.getRecipientClientId()).getName()
                : form.getRecipientName();

        if (form.isFromSalon()) {
            // purchaserClientId / purchaserName left null — displayed as "від салону"
        } else if (form.getPurchaserClientId() != null) {
            cert.setPurchaserClientId(form.getPurchaserClientId());
            cert.setPurchaserName(clientService.findById(form.getPurchaserClientId()).getName());
        } else {
            String note = LocalDate.now().format(NOTE_DATE) + " — покупка сертифіката"
                    + (recipientDisplay != null && !recipientDisplay.isBlank()
                       ? " для " + recipientDisplay.trim() : "");
            ClientDto newClient = clientService.save(ClientDto.builder()
                    .name(form.getPurchaserName().trim())
                    .phone(form.getPurchaserPhone())
                    .notes(note)
                    .build());
            cert.setPurchaserClientId(newClient.getId());
            cert.setPurchaserName(newClient.getName());
            cert.setPurchaserPhone(newClient.getPhone());
        }

        if (form.getRecipientClientId() != null) {
            cert.setRecipientClientId(form.getRecipientClientId());
            cert.setRecipientName(recipientDisplay);
            cert.setRecipientPhone(clientService.findById(form.getRecipientClientId()).getPhone());
        } else {
            String purchaserDisplay = cert.getPurchaserName();
            String recipientNote = LocalDate.now().format(NOTE_DATE) + " — отримувач сертифіката"
                    + (purchaserDisplay != null && !purchaserDisplay.isBlank()
                       ? " від " + purchaserDisplay.trim() : "");
            ClientDto newRecipient = clientService.save(ClientDto.builder()
                    .name(form.getRecipientName().trim())
                    .phone(form.getRecipientPhone())
                    .notes(recipientNote)
                    .build());
            cert.setRecipientClientId(newRecipient.getId());
            cert.setRecipientName(newRecipient.getName());
            cert.setRecipientPhone(newRecipient.getPhone());
        }

        SalonServiceDto svc = salonServiceService.findById(form.getServiceId());
        cert.setServiceId(svc.getId());
        cert.setServiceName(svc.getName());

        return repository.save(cert);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional
    public List<GiftCertificate> findAll() {
        syncExpired();
        return repository.findAllByOrderByIssuedAtDesc();
    }

    @Transactional
    public List<GiftCertificate> findAllByStatus(GiftCertificateStatus status) {
        syncExpired();
        return repository.findByStatusOrderByIssuedAtDesc(status);
    }

    /** Certificates where the client is either the purchaser OR the recipient. */
    @Transactional
    public List<GiftCertificate> findForClient(Long clientId) {
        syncExpired();
        return repository.findForClient(clientId);
    }

    @Transactional(readOnly = true)
    public GiftCertificate findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Gift certificate not found: " + id));
    }

    // ── Status transitions ────────────────────────────────────────────────────

    @Transactional
    public GiftCertificate redeem(Long id) {
        GiftCertificate cert = findById(id);
        if (cert.getStatus() != GiftCertificateStatus.ACTIVE) {
            throw new IllegalStateException("Certificate is not active");
        }
        cert.setStatus(GiftCertificateStatus.REDEEMED);
        cert.setRedeemedAt(LocalDateTime.now());
        return cert;
    }

    /** Restores a CANCELLED certificate back to ACTIVE. */
    @Transactional
    public GiftCertificate restore(Long id) {
        GiftCertificate cert = findById(id);
        if (cert.getStatus() != GiftCertificateStatus.CANCELLED) {
            throw new IllegalStateException("Only cancelled certificates can be restored");
        }
        cert.setStatus(GiftCertificateStatus.ACTIVE);
        cert.setCancelledAt(null);
        return cert;
    }

    /**
     * Hard-deletes a certificate. Blocked for REDEEMED certificates
     * because the visit payment record references the certificate code.
     */
    @Transactional
    public void delete(Long id) {
        GiftCertificate cert = findById(id);
        if (cert.getStatus() == GiftCertificateStatus.REDEEMED) {
            throw new IllegalStateException("gift.delete.error.redeemed");
        }
        repository.delete(cert);
    }

    @Transactional
    public GiftCertificate cancel(Long id) {
        GiftCertificate cert = findById(id);
        if (cert.getStatus() == GiftCertificateStatus.CANCELLED) {
            throw new IllegalStateException("Certificate is already cancelled");
        }
        cert.setStatus(GiftCertificateStatus.CANCELLED);
        cert.setCancelledAt(LocalDateTime.now());
        return cert;
    }

    // ── Certificate validity check (used by AJAX endpoint) ───────────────────

    /**
     * Returns the recipient name if the certificate code is ACTIVE, empty if not valid.
     * Use {@code .isPresent()} to check validity, {@code .orElse(null)} to get the name.
     */
    @Transactional
    public java.util.Optional<GiftCertificate> findIfValid(String code) {
        syncExpired();
        return repository.findByCode(code.trim().toUpperCase())
                .filter(c -> c.getStatus() == GiftCertificateStatus.ACTIVE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void syncExpired() {
        repository.expireOverdue(LocalDate.now());
    }

    // Alphabet excludes visually ambiguous chars: 0/O, 1/I/L, 5/S, 8/B
    private static final String CODE_ALPHABET = "ACDEFGHJKLMNPQRTUVWXY2346789";
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private String generateCode() {
        return randomPart(4) + "-" + randomPart(4);
    }

    private String randomPart(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
