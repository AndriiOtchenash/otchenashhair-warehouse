package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.task.CalendarTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface CalendarTaskRepository extends JpaRepository<CalendarTask, Long> {

    List<CalendarTask> findAllByTaskDateOrderByCreatedAtAsc(LocalDate taskDate);

    @Query("""
            SELECT t FROM CalendarTask t
            WHERE t.taskDate < :today AND t.done = false
            ORDER BY t.taskDate ASC, t.createdAt ASC
            """)
    List<CalendarTask> findOverduePending(@Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(t) FROM CalendarTask t
            WHERE t.taskDate < :today AND t.done = false
            """)
    long countOverduePending(@Param("today") LocalDate today);

    boolean existsByAppointmentId(Long appointmentId);

    java.util.Optional<CalendarTask> findFirstByAppointmentId(Long appointmentId);

    @Query("""
            SELECT t.taskDate, COUNT(t)
            FROM CalendarTask t
            WHERE t.taskDate BETWEEN :start AND :end
              AND t.done = false
            GROUP BY t.taskDate
            """)
    List<Object[]> countPendingByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
