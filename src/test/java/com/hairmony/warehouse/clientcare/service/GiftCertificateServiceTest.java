package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.GiftCertificateFormDto;
import com.hairmony.warehouse.clientcare.web.dto.SalonServiceDto;
import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
import com.hairmony.warehouse.repository.GiftCertificateRepository;
import com.hairmony.warehouse.service.ClientService;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GiftCertificateServiceTest {

    @Mock GiftCertificateRepository repository;
    @Mock ClientService clientService;
    @Mock SalonServiceService salonServiceService;

    @InjectMocks GiftCertificateService giftCertificateService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private GiftCertificate certWithStatus(Long id, GiftCertificateStatus status) {
        GiftCertificate c = new GiftCertificate();
        c.setStatus(status);
        when(repository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    // ── redeem() ─────────────────────────────────────────────────────────────

    @Test
    void redeem_activeCert_becomesRedeemed() {
        GiftCertificate cert = certWithStatus(1L, GiftCertificateStatus.ACTIVE);

        giftCertificateService.redeem(1L);

        assertThat(cert.getStatus()).isEqualTo(GiftCertificateStatus.REDEEMED);
        assertThat(cert.getRedeemedAt()).isNotNull();
    }

    @Test
    void redeem_nonActiveCert_throwsException() {
        certWithStatus(1L, GiftCertificateStatus.CANCELLED);

        assertThatThrownBy(() -> giftCertificateService.redeem(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    // ── restore() ────────────────────────────────────────────────────────────

    @Test
    void restore_cancelledCert_becomesActive() {
        GiftCertificate cert = certWithStatus(2L, GiftCertificateStatus.CANCELLED);
        cert.setCancelledAt(java.time.LocalDateTime.now().minusDays(1));

        giftCertificateService.restore(2L);

        assertThat(cert.getStatus()).isEqualTo(GiftCertificateStatus.ACTIVE);
        assertThat(cert.getCancelledAt()).isNull();
    }

    @Test
    void restore_activeCert_throwsException() {
        certWithStatus(2L, GiftCertificateStatus.ACTIVE);

        assertThatThrownBy(() -> giftCertificateService.restore(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void restore_redeemedCert_throwsException() {
        certWithStatus(2L, GiftCertificateStatus.REDEEMED);

        assertThatThrownBy(() -> giftCertificateService.restore(2L))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── cancel() ─────────────────────────────────────────────────────────────

    @Test
    void cancel_activeCert_becomesCancelled() {
        GiftCertificate cert = certWithStatus(3L, GiftCertificateStatus.ACTIVE);

        giftCertificateService.cancel(3L);

        assertThat(cert.getStatus()).isEqualTo(GiftCertificateStatus.CANCELLED);
        assertThat(cert.getCancelledAt()).isNotNull();
    }

    @Test
    void cancel_alreadyCancelled_throwsException() {
        certWithStatus(3L, GiftCertificateStatus.CANCELLED);

        assertThatThrownBy(() -> giftCertificateService.cancel(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled");
    }

    // ── delete() ─────────────────────────────────────────────────────────────

    @Test
    void delete_redeemedCert_throwsGuardException() {
        certWithStatus(4L, GiftCertificateStatus.REDEEMED);

        assertThatThrownBy(() -> giftCertificateService.delete(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gift.delete.error.redeemed");
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_activeCert_delegatesToRepository() {
        GiftCertificate cert = certWithStatus(4L, GiftCertificateStatus.ACTIVE);

        giftCertificateService.delete(4L);

        verify(repository).delete(cert);
    }

    @Test
    void delete_notFound_throwsEntityNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> giftCertificateService.delete(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── generateCode() via issue() ───────────────────────────────────────────

    // Chars that must NOT appear in the code (truly excluded from CODE_ALPHABET)
    private static final String EXCLUDED_CHARS = "0O1IS5B";

    @Test
    void issue_generatedCode_hasCorrectFormat() {
        // Minimal form: fromSalon=true, existing recipient client, existing service
        GiftCertificateFormDto form = new GiftCertificateFormDto();
        form.setFromSalon(true);
        form.setRecipientClientId(5L);
        form.setServiceId(1L);
        form.setExpiresAt(LocalDate.now().plusMonths(6));
        form.setPrice(BigDecimal.ZERO);

        ClientDto recipientDto = ClientDto.builder().id(5L).name("Test Recipient").phone("+48111222333").build();
        when(clientService.findById(5L)).thenReturn(recipientDto);

        SalonServiceDto svcDto = new SalonServiceDto();
        svcDto.setId(1L);
        svcDto.setName("Konsultacja");
        when(salonServiceService.findById(1L)).thenReturn(svcDto);

        when(repository.save(any(GiftCertificate.class))).thenAnswer(inv -> inv.getArgument(0));

        GiftCertificate cert = giftCertificateService.issue(form);

        assertThat(cert.getCode())
                .matches("[A-Z0-9]{4}-[A-Z0-9]{4}")
                .hasSize(9);
    }

    @Test
    void issue_generatedCode_containsNoExcludedChars() {
        GiftCertificateFormDto form = new GiftCertificateFormDto();
        form.setFromSalon(true);
        form.setRecipientClientId(5L);
        form.setServiceId(1L);
        form.setExpiresAt(LocalDate.now().plusMonths(6));
        form.setPrice(BigDecimal.ZERO);

        ClientDto recipientDto = ClientDto.builder().id(5L).name("Test").phone("+48111").build();
        when(clientService.findById(5L)).thenReturn(recipientDto);

        SalonServiceDto svcDto = new SalonServiceDto();
        svcDto.setId(1L);
        svcDto.setName("Konsultacja");
        when(salonServiceService.findById(1L)).thenReturn(svcDto);
        when(repository.save(any(GiftCertificate.class))).thenAnswer(inv -> inv.getArgument(0));

        // Run many iterations to reduce false-negative probability
        for (int i = 0; i < 50; i++) {
            GiftCertificate cert = giftCertificateService.issue(form);
            String codeChars = cert.getCode().replace("-", "");
            for (char ch : EXCLUDED_CHARS.toCharArray()) {
                assertThat(codeChars).doesNotContain(String.valueOf(ch));
            }
        }
    }

    // ── findIfValid() ─────────────────────────────────────────────────────────

    @Test
    void findIfValid_activeCert_returnsPresent() {
        GiftCertificate active = new GiftCertificate();
        active.setStatus(GiftCertificateStatus.ACTIVE);
        when(repository.findByCode("ABCD-EFGH")).thenReturn(Optional.of(active));

        assertThat(giftCertificateService.findIfValid("ABCD-EFGH")).isPresent();
    }

    @Test
    void findIfValid_expiredCert_returnsEmpty() {
        GiftCertificate expired = new GiftCertificate();
        expired.setStatus(GiftCertificateStatus.EXPIRED);
        when(repository.findByCode("ABCD-EFGH")).thenReturn(Optional.of(expired));

        assertThat(giftCertificateService.findIfValid("ABCD-EFGH")).isEmpty();
    }

    @Test
    void findIfValid_notFound_returnsEmpty() {
        when(repository.findByCode("ZZZZ-ZZZZ")).thenReturn(Optional.empty());

        assertThat(giftCertificateService.findIfValid("ZZZZ-ZZZZ")).isEmpty();
    }
}
