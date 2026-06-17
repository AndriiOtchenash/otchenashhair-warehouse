package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.domain.appointment.PaymentMethod;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
import com.hairmony.warehouse.domain.visit.Visit;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.GiftCertificateRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VisitServiceTest {

    @Mock VisitRepository visitRepository;
    @Mock ClientRepository clientRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock GiftCertificateRepository certificateRepository;

    @InjectMocks VisitService visitService;

    private static final long CLIENT_ID = 1L;
    private static final String CERT_CODE = "ABCD-EFGH";

    // ── helpers ──────────────────────────────────────────────────────────────

    private VisitDto baseDto(PaymentMethod method) {
        VisitDto dto = new VisitDto();
        dto.setClientId(CLIENT_ID);
        dto.setVisitDate(LocalDate.now());
        dto.setPaymentMethod(method);
        dto.setPriceAtTime(new BigDecimal("100"));
        return dto;
    }

    private void stubSave() {
        Client client = Client.builder().id(CLIENT_ID).name("Test").build();
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Optional.of(client));
        when(visitRepository.save(any(Visit.class))).thenAnswer(inv -> {
            Visit v = inv.getArgument(0);
            v.setId(99L);
            return v;
        });
    }

    private GiftCertificate cert(GiftCertificateStatus status) {
        GiftCertificate c = new GiftCertificate();
        c.setStatus(status);
        return c;
    }

    // ── AUTO_PAID methods → paid=true, priceAtTime=null ──────────────────────

    @ParameterizedTest(name = "{0} → auto-paid")
    @EnumSource(value = PaymentMethod.class, names = {"BARTER", "COMPLIMENTARY", "PROMO"})
    void autoPaidMethods_setsPaidAndClearsPrice(PaymentMethod method) {
        stubSave();
        VisitDto dto = baseDto(method);

        visitService.save(dto);

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(captor.capture());
        assertThat(captor.getValue().isPaid()).isTrue();
        assertThat(captor.getValue().getPriceAtTime()).isNull();
        verify(certificateRepository, never()).findByCode(anyString());
    }

    // ── CASH → no auto-paid logic ────────────────────────────────────────────

    @Test
    void cashPayment_doesNotModifyPaidFlag() {
        stubSave();
        VisitDto dto = baseDto(PaymentMethod.CASH);
        dto.setPaid(false);

        visitService.save(dto);

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(captor.capture());
        assertThat(captor.getValue().isPaid()).isFalse();
        assertThat(captor.getValue().getPriceAtTime()).isEqualByComparingTo("100");
    }

    // ── CERTIFICATE — valid ACTIVE cert ──────────────────────────────────────

    @Test
    void certificate_activeCert_becomesRedeemed() {
        stubSave();
        GiftCertificate activeCert = cert(GiftCertificateStatus.ACTIVE);
        when(certificateRepository.findByCode(CERT_CODE)).thenReturn(Optional.of(activeCert));

        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode(CERT_CODE);

        visitService.save(dto);

        assertThat(activeCert.getStatus()).isEqualTo(GiftCertificateStatus.REDEEMED);
        assertThat(activeCert.getRedeemedAt()).isNotNull();

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepository).save(captor.capture());
        assertThat(captor.getValue().isPaid()).isTrue();
    }

    // ── CERTIFICATE — error states ────────────────────────────────────────────

    @Test
    void certificate_expiredCert_throwsExpiredError() {
        when(certificateRepository.findByCode(CERT_CODE)).thenReturn(Optional.of(cert(GiftCertificateStatus.EXPIRED)));

        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode(CERT_CODE);

        assertThatThrownBy(() -> visitService.save(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("visit.certificate.expired");
        verify(visitRepository, never()).save(any());
    }

    @Test
    void certificate_redeemedCert_throwsNotActiveError() {
        when(certificateRepository.findByCode(CERT_CODE)).thenReturn(Optional.of(cert(GiftCertificateStatus.REDEEMED)));

        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode(CERT_CODE);

        assertThatThrownBy(() -> visitService.save(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("visit.certificate.notActive");
        verify(visitRepository, never()).save(any());
    }

    @Test
    void certificate_notFound_throwsNotFoundError() {
        when(certificateRepository.findByCode(CERT_CODE)).thenReturn(Optional.empty());

        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode(CERT_CODE);

        assertThatThrownBy(() -> visitService.save(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("visit.certificate.notFound");
    }

    @Test
    void certificate_blankCode_skipsCertLookup() {
        stubSave();
        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode("   "); // blank → trimOrNull → null → early return

        visitService.save(dto);

        verify(certificateRepository, never()).findByCode(anyString());
    }

    // ── update() — same cert already applied guard ────────────────────────────

    @Test
    void update_sameCertCodeAlreadyApplied_skipsRedemption() {
        Visit existing = new Visit();
        existing.setCertificateCode(CERT_CODE);
        existing.setPaid(true);
        existing.setVisitDate(LocalDate.now());
        existing.setClient(Client.builder().id(CLIENT_ID).build());
        when(visitRepository.findById(1L)).thenReturn(Optional.of(existing));

        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode(CERT_CODE); // same code as already on the visit

        visitService.update(1L, dto);

        verify(certificateRepository, never()).findByCode(anyString());
        assertThat(dto.isPaid()).isTrue(); // kept as paid
    }

    @Test
    void update_newCertCode_appliesNewCertRedemption() {
        String oldCode = "OLD1-OLD2";
        String newCode = "NEW1-NEW2";

        Visit existing = new Visit();
        existing.setCertificateCode(oldCode);
        existing.setVisitDate(LocalDate.now());
        existing.setClient(Client.builder().id(CLIENT_ID).build());
        when(visitRepository.findById(1L)).thenReturn(Optional.of(existing));

        GiftCertificate newCert = cert(GiftCertificateStatus.ACTIVE);
        when(certificateRepository.findByCode(newCode)).thenReturn(Optional.of(newCert));

        VisitDto dto = baseDto(PaymentMethod.CERTIFICATE);
        dto.setCertificateCode(newCode); // different code → triggers applyCertificatePayment

        visitService.update(1L, dto);

        assertThat(newCert.getStatus()).isEqualTo(GiftCertificateStatus.REDEEMED);
    }
}
