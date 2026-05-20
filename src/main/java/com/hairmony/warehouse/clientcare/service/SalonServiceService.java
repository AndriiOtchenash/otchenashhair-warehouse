package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.SalonServiceDto;
import com.hairmony.warehouse.domain.service.SalonService;
import com.hairmony.warehouse.repository.SalonServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SalonServiceService {

    private final SalonServiceRepository repository;

    @Transactional(readOnly = true)
    public List<SalonServiceDto> findAllActive() {
        return repository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalonServiceDto> findAll() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SalonServiceDto findById(Long id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id)));
    }

    public SalonServiceDto save(SalonServiceDto dto) {
        SalonService entity = new SalonService();
        entity.setName(dto.getName().trim());
        entity.setDescription(dto.getDescription());
        entity.setActive(true);
        return toDto(repository.save(entity));
    }

    public void update(SalonServiceDto dto) {
        SalonService entity = repository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + dto.getId()));
        entity.setName(dto.getName().trim());
        entity.setDescription(dto.getDescription());
        // dirty checking — no explicit save()
    }

    public void deactivate(Long id) {
        SalonService entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));
        entity.setActive(false);
    }

    public void activate(Long id) {
        SalonService entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + id));
        entity.setActive(true);
    }

    private SalonServiceDto toDto(SalonService e) {
        SalonServiceDto dto = new SalonServiceDto();
        dto.setId(e.getId());
        dto.setName(e.getName());
        dto.setDescription(e.getDescription());
        dto.setActive(e.isActive());
        return dto;
    }
}
