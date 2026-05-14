package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.FollowUpActivityDto;
import com.hairmony.warehouse.clientcare.web.dto.LatestFollowUpDto;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.followup.FollowUp;
import com.hairmony.warehouse.domain.followup.FollowUpAction;
import com.hairmony.warehouse.repository.FollowUpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final FollowUpRepository followUpRepository;

    @Transactional(readOnly = true)
    public Map<Long, LatestFollowUpDto> getLatestPerClient(Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return Map.of();
        return followUpRepository.findLatestPerClient(clientIds).stream()
                .collect(Collectors.toMap(
                        f -> f.getClient().getId(),
                        f -> new LatestFollowUpDto(
                                f.getClient().getId(),
                                f.getAction(),
                                f.getDueDate(),
                                f.getNote()
                        )
                ));
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> getActivityCountsPerClient(Collection<Long> clientIds) {
        if (clientIds.isEmpty()) return Map.of();
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (Object[] row : followUpRepository.countPerClient(clientIds)) {
            result.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<FollowUpActivityDto> getActivityForClient(Long clientId) {
        return followUpRepository.findAllByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(f -> new FollowUpActivityDto(
                        f.getId(),
                        f.getAction(),
                        f.getDueDate(),
                        f.getNote(),
                        f.getCreatedAt()
                ))
                .toList();
    }

    private static final int DONE_AUTO_HIDE_DAYS = 7;

    @Transactional
    public void done(Long clientId, String note) {
        String n = (note != null && !note.isBlank()) ? note.trim() : null;
        // Auto-hide from queue for 7 days (Variant A)
        save(clientId, FollowUpAction.DONE, LocalDate.now().plusDays(DONE_AUTO_HIDE_DAYS), n);
    }

    @Transactional
    public void snooze(Long clientId, int days, String note) {
        String n = (note != null && !note.isBlank()) ? note.trim() : null;
        save(clientId, FollowUpAction.SNOOZE, LocalDate.now().plusDays(days), n);
    }

    @Transactional
    public void note(Long clientId, String text) {
        save(clientId, FollowUpAction.NOTE, null, text);
    }

    @Transactional
    public void deleteAllActivity(Long clientId) {
        followUpRepository.deleteAllByClientId(clientId);
    }

    @Transactional
    public void deleteActivity(Long id) {
        FollowUp f = followUpRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Activity record not found: " + id));
        followUpRepository.delete(f);
    }

    @Transactional
    public void reset(Long clientId) {
        followUpRepository.findFirstByClientIdOrderByCreatedAtDesc(clientId)
                .ifPresent(followUpRepository::delete);
    }

    /** Removes all active SNOOZE and DONE records — client returns to active queue immediately */
    @Transactional
    public void returnToQueue(Long clientId) {
        followUpRepository.deleteActiveHidingRecords(
                clientId,
                List.of(FollowUpAction.SNOOZE, FollowUpAction.DONE),
                LocalDate.now()
        );
    }

    private void save(Long clientId, FollowUpAction action, LocalDate dueDate, String note) {
        Client client = new Client();
        client.setId(clientId);

        FollowUp followUp = new FollowUp();
        followUp.setClient(client);
        followUp.setAction(action);
        followUp.setDueDate(dueDate);
        followUp.setNote(note);
        followUpRepository.save(followUp);
    }
}
