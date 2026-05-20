package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.service.SalonService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalonServiceRepository extends JpaRepository<SalonService, Long> {

    List<SalonService> findAllByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
