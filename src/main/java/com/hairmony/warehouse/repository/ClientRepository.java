package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.client.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findAllByOrderByNameAsc();
    Optional<Client> findByTelegramLinkToken(String token);
}
