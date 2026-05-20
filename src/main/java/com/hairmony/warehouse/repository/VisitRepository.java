package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.visit.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    List<Visit> findAllByClientIdOrderByVisitDateDescCreatedAtDesc(Long clientId);

    boolean existsByClientId(Long clientId);

    /** All client IDs that have at least one visit (for deletion guard). */
    @Query("SELECT DISTINCT v.client.id FROM Visit v")
    Set<Long> findAllClientIdsWithVisits();

    Optional<Visit> findFirstByClientIdOrderByVisitDateDescCreatedAtDesc(Long clientId);

    Optional<Visit> findByNextAppointmentId(Long appointmentId);

    /** Client IDs that have at least one visit with billing data but not yet paid. */
    @Query("SELECT DISTINCT v.client.id FROM Visit v WHERE v.paid = false AND (v.priceAtTime IS NOT NULL OR v.paymentMethod IS NOT NULL)")
    Set<Long> findClientIdsWithUnpaidVisits();

    /**
     * Returns the latest visit per client where nextVisitDate is set (any date).
     * Eagerly fetches client to avoid lazy-load outside transaction.
     */
    @Query("""
        SELECT v FROM Visit v
        JOIN FETCH v.client
        WHERE v.nextVisitDate IS NOT NULL
          AND v.visitDate = (
              SELECT MAX(v2.visitDate) FROM Visit v2
              WHERE v2.client.id = v.client.id
                AND v2.nextVisitDate IS NOT NULL
          )
        ORDER BY v.nextVisitDate ASC
    """)
    List<Visit> findAllLatestWithNextVisitDate();
}
