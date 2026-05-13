package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.VisitDto;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.visit.Visit;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<VisitDto> findByClientId(Long clientId) {
        return visitRepository.findAllByClientIdOrderByVisitDateDescCreatedAtDesc(clientId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public VisitDto findById(Long id) {
        return toDto(visitRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("visit.notFound")));
    }

    @Transactional
    public void save(VisitDto dto) {
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> new IllegalStateException("client.notFound"));
        Visit visit = Visit.builder()
                .client(client)
                .visitDate(dto.getVisitDate())
                .complaint(dto.getComplaint())
                .scalpCondition(dto.getScalpCondition())
                .recommendations(dto.getRecommendations())
                .nextVisitDate(dto.getNextVisitDate())
                .notes(dto.getNotes())
                .build();
        visitRepository.save(visit);
    }

    @Transactional
    public void update(Long id, VisitDto dto) {
        Visit visit = visitRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("visit.notFound"));
        visit.setVisitDate(dto.getVisitDate());
        visit.setComplaint(dto.getComplaint());
        visit.setScalpCondition(dto.getScalpCondition());
        visit.setRecommendations(dto.getRecommendations());
        visit.setNextVisitDate(dto.getNextVisitDate());
        visit.setNotes(dto.getNotes());
    }

    @Transactional
    public void delete(Long id) {
        visitRepository.deleteById(id);
    }

    private VisitDto toDto(Visit v) {
        VisitDto dto = new VisitDto();
        dto.setId(v.getId());
        dto.setClientId(v.getClient().getId());
        dto.setVisitDate(v.getVisitDate());
        dto.setComplaint(v.getComplaint());
        dto.setScalpCondition(v.getScalpCondition());
        dto.setRecommendations(v.getRecommendations());
        dto.setNextVisitDate(v.getNextVisitDate());
        dto.setNotes(v.getNotes());
        dto.setCreatedAt(v.getCreatedAt());
        return dto;
    }
}
