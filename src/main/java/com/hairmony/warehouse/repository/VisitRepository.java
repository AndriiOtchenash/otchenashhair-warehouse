package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.visit.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface VisitRepository extends JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {

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
     * Client IDs whose latest visit has no next appointment scheduled
     * and the practitioner hasn't explicitly marked it as not required,
     * AND the client has no upcoming PLANNED/CONFIRMED appointment in the calendar.
     */
    @Query("""
        SELECT DISTINCT v.client.id FROM Visit v
        WHERE v.nextVisitSkipped = false
          AND v.nextAppointment IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM Visit v2
              WHERE v2.client.id = v.client.id
                AND (v2.visitDate > v.visitDate
                     OR (v2.visitDate = v.visitDate AND v2.createdAt > v.createdAt))
          )
          AND NOT EXISTS (
              SELECT 1 FROM Appointment a
              WHERE a.client.id = v.client.id
                AND a.status IN (com.hairmony.warehouse.domain.appointment.AppointmentStatus.PLANNED,
                                 com.hairmony.warehouse.domain.appointment.AppointmentStatus.CONFIRMED)
                AND a.startAt > CURRENT_TIMESTAMP
          )
    """)
    Set<Long> findClientIdsWithUnresolvedNextVisit();

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

    /** All visits in a date range with client eagerly fetched — used for finance reporting. */
    @Query("SELECT v FROM Visit v JOIN FETCH v.client WHERE v.visitDate BETWEEN :from AND :to ORDER BY v.visitDate DESC")
    List<Visit> findWithClientByPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Earliest visit date — used for ALL_TIME period preset. */
    @Query("SELECT MIN(v.visitDate) FROM Visit v")
    Optional<LocalDate> findEarliestVisitDate();

    /**
     * Returns the latest visit per client where next visit was explicitly skipped
     * (nextVisitSkipped=true, no newer visit). Used for the "skipped" collapsed section
     * in the follow-up tab so practitioners can undo accidental skips.
     */
    @Query("""
        SELECT v FROM Visit v
        JOIN FETCH v.client
        WHERE v.nextVisitSkipped = true
          AND NOT EXISTS (
              SELECT 1 FROM Visit v2
              WHERE v2.client.id = v.client.id
                AND (v2.visitDate > v.visitDate
                     OR (v2.visitDate = v.visitDate AND v2.createdAt > v.createdAt))
          )
        ORDER BY v.visitDate DESC, v.createdAt DESC
    """)
    List<Visit> findLatestSkippedVisitsPerClient();

    /**
     * Returns the latest visit per client where next visit is unresolved
     * (nextVisitSkipped=false, no nextAppointment, no newer visit,
     * and no upcoming PLANNED/CONFIRMED appointment in the calendar).
     * Eagerly fetches client to avoid lazy-load outside transaction.
     * Sorted by visitDate ASC (longest overdue first).
     */
    @Query("""
        SELECT v FROM Visit v
        JOIN FETCH v.client
        WHERE v.nextVisitSkipped = false
          AND v.nextAppointment IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM Visit v2
              WHERE v2.client.id = v.client.id
                AND (v2.visitDate > v.visitDate
                     OR (v2.visitDate = v.visitDate AND v2.createdAt > v.createdAt))
          )
          AND NOT EXISTS (
              SELECT 1 FROM Appointment a
              WHERE a.client.id = v.client.id
                AND a.status IN (com.hairmony.warehouse.domain.appointment.AppointmentStatus.PLANNED,
                                 com.hairmony.warehouse.domain.appointment.AppointmentStatus.CONFIRMED)
                AND a.startAt > CURRENT_TIMESTAMP
          )
        ORDER BY v.visitDate ASC
    """)
    List<Visit> findLatestUnresolvedVisitsPerClient();

}
