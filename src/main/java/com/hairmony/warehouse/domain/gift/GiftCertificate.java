package com.hairmony.warehouse.domain.gift;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "gift_certificates")
@Getter
@Setter
@NoArgsConstructor
public class GiftCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(name = "purchaser_client_id")
    private Long purchaserClientId;

    @Column(name = "purchaser_name", length = 200)
    private String purchaserName;

    @Column(name = "purchaser_phone", length = 50)
    private String purchaserPhone;

    @Column(name = "recipient_client_id")
    private Long recipientClientId;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "recipient_phone", length = 50)
    private String recipientPhone;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GiftCertificateStatus status;

    @Column(length = 2000)
    private String notes;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        if (this.issuedAt == null) this.issuedAt = LocalDateTime.now();
        if (this.status == null) this.status = GiftCertificateStatus.ACTIVE;
    }

    /** Days until expiry. Negative if already expired. */
    public long daysRemaining() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiresAt);
    }

    public boolean isActive()    { return status == GiftCertificateStatus.ACTIVE; }
    public boolean isRedeemed()  { return status == GiftCertificateStatus.REDEEMED; }
    public boolean isExpired()   { return status == GiftCertificateStatus.EXPIRED; }
    public boolean isCancelled() { return status == GiftCertificateStatus.CANCELLED; }
}
