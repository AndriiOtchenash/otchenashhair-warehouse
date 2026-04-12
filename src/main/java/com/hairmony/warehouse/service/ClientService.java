package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.web.dto.ClientDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;

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

    public void delete(Long id) {
        clientRepository.deleteById(id);
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
