package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.client.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
