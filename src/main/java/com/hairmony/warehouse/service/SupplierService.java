package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.supplier.Supplier;
import com.hairmony.warehouse.repository.StockMovementRepository;
import com.hairmony.warehouse.repository.SupplierRepository;
import com.hairmony.warehouse.web.dto.SupplierDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public List<SupplierDto> findAll() {
        return supplierRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto findById(Long id) {
        return supplierRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found: " + id));
    }

    public SupplierDto save(SupplierDto dto) {
        return toDto(supplierRepository.save(toEntity(dto)));
    }

    public SupplierDto update(Long id, SupplierDto dto) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found: " + id));
        supplier.setName(dto.getName());
        supplier.setContactInfo(dto.getContactInfo());
        supplier.setNotes(dto.getNotes());
        return toDto(supplier); // dirty checking
    }

    public String delete(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Supplier not found: " + id));
        if (stockMovementRepository.existsBySupplierId(id)) {
            throw new IllegalStateException(messageSource.getMessage(
                    "supplier.delete.error.hasMovements", null, LocaleContextHolder.getLocale()));
        }
        String name = supplier.getName();
        supplierRepository.deleteById(id);
        return name;
    }

    @Transactional(readOnly = true)
    public Set<Long> getSupplierIdsWithMovements() {
        return stockMovementRepository.findAllSupplierIdsWithMovements();
    }

    public List<Supplier> findAllEntities() {
        return supplierRepository.findAllByOrderByNameAsc();
    }

    private SupplierDto toDto(Supplier s) {
        return SupplierDto.builder()
                .id(s.getId())
                .name(s.getName())
                .contactInfo(s.getContactInfo())
                .notes(s.getNotes())
                .build();
    }

    private Supplier toEntity(SupplierDto dto) {
        return Supplier.builder()
                .id(dto.getId())
                .name(dto.getName())
                .contactInfo(dto.getContactInfo())
                .notes(dto.getNotes())
                .build();
    }
}
