package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findAllByStartAtBetweenOrderByStartAtAsc(LocalDateTime from, LocalDateTime to);

    List<Appointment> findAllByClientIdOrderByStartAtAsc(Long clientId);

    boolean existsByClientId(Long clientId);

    /** All client IDs that have at least one appointment (for deletion guard). */
    @Query("SELECT DISTINCT a.client.id FROM Appointment a WHERE a.client IS NOT NULL")
    Set<Long> findAllClientIdsWithAppointments();

    /** Upcoming/today appointments for linked clients (not guests), ordered earliest first. */
    @Query("SELECT a FROM Appointment a JOIN FETCH a.client WHERE a.startAt >= :from AND a.status IN :statuses ORDER BY a.startAt ASC")
    List<Appointment> findUpcomingForClients(@Param("from") LocalDateTime from,
                                             @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Past appointments for linked clients that were not completed/cancelled — overdue. */
    @Query("SELECT a FROM Appointment a JOIN FETCH a.client WHERE a.startAt < :before AND a.status IN :statuses ORDER BY a.startAt DESC")
    List<Appointment> findOverdueForClients(@Param("before") LocalDateTime before,
                                            @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Client IDs with a linked appointment starting in [from, to] (for list-page icons). */
    @Query("SELECT DISTINCT a.client.id FROM Appointment a WHERE a.client IS NOT NULL AND a.startAt BETWEEN :from AND :to AND a.status IN :statuses")
    Set<Long> findClientIdsWithUpcomingBetween(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Client IDs with any future linked appointment (no upper bound, for list-page icons). */
    @Query("SELECT DISTINCT a.client.id FROM Appointment a WHERE a.client IS NOT NULL AND a.startAt >= :from AND a.status IN :statuses")
    Set<Long> findClientIdsWithAnyUpcomingFrom(@Param("from") LocalDateTime from,
                                               @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Client IDs with a linked appointment whose startAt is before :before (for list-page overdue icons). */
    @Query("SELECT DISTINCT a.client.id FROM Appointment a WHERE a.client IS NOT NULL AND a.startAt < :before AND a.status IN :statuses")
    Set<Long> findClientIdsWithOverdueBefore(@Param("before") LocalDateTime before,
                                             @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Earliest upcoming or in-progress appointment for a single client (limit 1). */
    @Query("SELECT a FROM Appointment a WHERE a.client.id = :clientId AND a.endAt >= :from AND a.status IN :statuses ORDER BY a.startAt ASC")
    List<Appointment> findUpcomingByClientId(@Param("clientId") Long clientId,
                                             @Param("from") LocalDateTime from,
                                             @Param("statuses") Collection<AppointmentStatus> statuses);

    /** Most-recent overdue appointment for a single client (limit 1). */
    @Query("SELECT a FROM Appointment a WHERE a.client.id = :clientId AND a.startAt < :before AND a.status IN :statuses ORDER BY a.startAt DESC")
    List<Appointment> findOverdueByClientId(@Param("clientId") Long clientId,
                                            @Param("before") LocalDateTime before,
                                            @Param("statuses") Collection<AppointmentStatus> statuses);

    // ── Reminder queries ──────────────────────────────────────────────────────

    @Query("SELECT a FROM Appointment a JOIN FETCH a.client WHERE a.startAt BETWEEN :from AND :to AND a.status IN :statuses AND a.reminder48hSentAt IS NULL")
    List<Appointment> findAppointmentsFor48hReminder(@Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to,
                                                     @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.client WHERE a.startAt >= :dayStart AND a.startAt < :dayEnd AND a.status IN :statuses AND a.reminder24hSentAt IS NULL")
    List<Appointment> findAppointmentsForDayReminder(@Param("dayStart") LocalDateTime dayStart,
                                                     @Param("dayEnd") LocalDateTime dayEnd,
                                                     @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.client WHERE a.startAt BETWEEN :from AND :to AND a.status IN :statuses AND a.reminder2hSentAt IS NULL")
    List<Appointment> findAppointmentsFor2hReminder(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to,
                                                    @Param("statuses") Collection<AppointmentStatus> statuses);
}
