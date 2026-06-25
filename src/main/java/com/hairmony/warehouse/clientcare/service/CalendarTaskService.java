package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.CalendarTaskDto;
import com.hairmony.warehouse.domain.task.CalendarTask;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.CalendarTaskRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CalendarTaskService {

    private final CalendarTaskRepository repo;
    private final ClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Transactional(readOnly = true)
    public List<CalendarTaskDto> findByDate(LocalDate date) {
        LocalDate today = LocalDate.now(WARSAW);
        List<CalendarTask> tasks = new ArrayList<>();
        if (date.equals(today)) {
            tasks.addAll(repo.findOverduePending(today)); // past-due first
        }
        tasks.addAll(repo.findAllByTaskDateOrderByCreatedAtAsc(date));
        return tasks.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getCountsByDateRange(LocalDate start, LocalDate end) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        repo.countPendingByDateRange(start, end)
                .forEach(row -> result.put((LocalDate) row[0], (Long) row[1]));
        LocalDate today = LocalDate.now(WARSAW);
        if (!today.isBefore(start) && !today.isAfter(end)) {
            long overdue = repo.countOverduePending(today);
            if (overdue > 0) {
                result.merge(today, overdue, Long::sum);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean existsByAppointmentId(Long appointmentId) {
        return repo.existsByAppointmentId(appointmentId);
    }

    @Transactional(readOnly = true)
    public Optional<CalendarTaskDto> findByAppointmentId(Long appointmentId) {
        return repo.findFirstByAppointmentId(appointmentId).map(this::toDto);
    }

    @Transactional
    public CalendarTaskDto update(Long id, LocalDate taskDate, String text) {
        CalendarTask task = repo.findById(id).orElseThrow();
        task.setTaskDate(taskDate);
        task.setText(text);
        return toDto(task);
    }

    @Transactional
    public CalendarTaskDto save(LocalDate taskDate, String text, Long clientId, Long appointmentId) {
        CalendarTask task = new CalendarTask();
        task.setTaskDate(taskDate);
        task.setText(text);
        if (clientId != null) {
            task.setClient(clientRepository.getReferenceById(clientId));
        }
        if (appointmentId != null) {
            task.setAppointment(appointmentRepository.getReferenceById(appointmentId));
        }
        return toDto(repo.save(task));
    }

    @Transactional
    public void toggleDone(Long id) {
        repo.findById(id).ifPresent(t -> t.setDone(!t.isDone()));
    }

    @Transactional
    public void delete(Long id) {
        repo.deleteById(id);
    }

    private CalendarTaskDto toDto(CalendarTask t) {
        return new CalendarTaskDto(
                t.getId(),
                t.getTaskDate(),
                t.getClient() != null ? t.getClient().getId() : null,
                t.getClient() != null ? t.getClient().getName() : null,
                t.getAppointment() != null ? t.getAppointment().getId() : null,
                t.getText(),
                t.isDone(),
                t.getCreatedAt()
        );
    }
}
