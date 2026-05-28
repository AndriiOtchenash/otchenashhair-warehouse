package com.hairmony.warehouse.service;

import com.hairmony.warehouse.clientcare.service.GiftCertificateService;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.StockMovementRepository;
import com.hairmony.warehouse.repository.VisitRepository;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private static final Pattern DRIVE_FOLDER_ID_PATTERN =
            Pattern.compile("folders/([a-zA-Z0-9_-]{10,})");

    private final ClientRepository clientRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AppointmentRepository appointmentRepository;
    private final VisitRepository visitRepository;

    @Autowired @Lazy
    private GiftCertificateService giftCertificateService;

    @Transactional(readOnly = true)
    public List<ClientDto> findAll() {
        return clientRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDto findById(Long id) {
        return clientRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));
    }

    public ClientDto save(ClientDto dto) {
        return toDto(clientRepository.save(toEntity(dto)));
    }

    public ClientDto update(Long id, ClientDto dto) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));
        client.setName(dto.getName());
        client.setPhone(dto.getPhone());
        client.setNotes(dto.getNotes());
        return toDto(client);
    }

    public void removeDriveFolder(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));
        client.setDriveFolderUrl(null);
        client.setDriveFolderId(null);
    }

    public void updateDriveFolder(Long id, String url) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));
        String trimmed = url != null ? url.trim() : null;
        client.setDriveFolderUrl(trimmed);
        Matcher m = trimmed != null ? DRIVE_FOLDER_ID_PATTERN.matcher(trimmed) : null;
        client.setDriveFolderId(m != null && m.find() ? m.group(1) : null);
    }

    public void delete(Long id) {
        if (stockMovementRepository.existsByClientId(id)) {
            throw new IllegalStateException("client.delete.error.hasMovements");
        }
        if (appointmentRepository.existsByClientId(id)) {
            throw new IllegalStateException("client.delete.error.hasAppointments");
        }
        if (visitRepository.existsByClientId(id)) {
            throw new IllegalStateException("client.delete.error.hasVisits");
        }
        if (!giftCertificateService.findForClient(id).isEmpty()) {
            throw new IllegalStateException("client.delete.error.hasCertificates");
        }
        clientRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Set<Long> getClientIdsNotDeletable() {
        Set<Long> ids = new java.util.HashSet<>(stockMovementRepository.findAllClientIdsWithMovements());
        ids.addAll(appointmentRepository.findAllClientIdsWithAppointments());
        ids.addAll(visitRepository.findAllClientIdsWithVisits());
        giftCertificateService.findAll().forEach(c -> {
            if (c.getPurchaserClientId() != null) ids.add(c.getPurchaserClientId());
            if (c.getRecipientClientId() != null) ids.add(c.getRecipientClientId());
        });
        return ids;
    }

    public List<Client> findAllEntities() {
        return clientRepository.findAllByOrderByNameAsc();
    }

    private ClientDto toDto(Client c) {
        return ClientDto.builder()
                .id(c.getId())
                .name(c.getName())
                .phone(c.getPhone())
                .notes(c.getNotes())
                .driveFolderUrl(c.getDriveFolderUrl())
                .driveFolderId(c.getDriveFolderId())
                .telegramChatId(c.getTelegramChatId())
                .build();
    }

    private Client toEntity(ClientDto dto) {
        return Client.builder()
                .id(dto.getId())
                .name(dto.getName())
                .phone(dto.getPhone())
                .notes(dto.getNotes())
                .build();
    }
}
