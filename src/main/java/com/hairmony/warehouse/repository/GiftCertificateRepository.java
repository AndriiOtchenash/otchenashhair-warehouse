package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.gift.GiftCertificate;
import com.hairmony.warehouse.domain.gift.GiftCertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GiftCertificateRepository extends JpaRepository<GiftCertificate, Long> {

    List<GiftCertificate> findAllByOrderByIssuedAtDesc();

    Optional<GiftCertificate> findByCode(String code);

    List<GiftCertificate> findByStatusOrderByIssuedAtDesc(GiftCertificateStatus status);

    @Query("SELECT g FROM GiftCertificate g WHERE g.purchaserClientId = :clientId OR g.recipientClientId = :clientId ORDER BY g.issuedAt DESC")
    List<GiftCertificate> findForClient(@Param("clientId") Long clientId);

    /** Marks all ACTIVE certificates past their expiry date as EXPIRED. */
    /** Marks all ACTIVE certificates past their expiry date as EXPIRED.
     *  REQUIRES_NEW: always runs in its own writable transaction,
     *  even when called from a read-only context (e.g. ClientService.getClientIdsNotDeletable). */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("UPDATE GiftCertificate g SET g.status = com.hairmony.warehouse.domain.gift.GiftCertificateStatus.EXPIRED WHERE g.status = com.hairmony.warehouse.domain.gift.GiftCertificateStatus.ACTIVE AND g.expiresAt < :today")
    int expireOverdue(@Param("today") LocalDate today);
}
