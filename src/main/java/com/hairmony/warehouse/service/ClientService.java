package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.web.dto.ClientDto;
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
        return clientRepository.findAll().stream()
                .map(c -> ClientDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .phone(c.getPhone())
                        .notes(c.getNotes())
                        .build())
                .toList();
    }

    public List<Client> findAllEntities() {
        return clientRepository.findAll();
    }
}
