package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.visit.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    List<Visit> findAllByClientIdOrderByVisitDateDescCreatedAtDesc(Long clientId);
}
