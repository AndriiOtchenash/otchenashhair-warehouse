package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.scalp.ScalpPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScalpPhotoRepository extends JpaRepository<ScalpPhoto, Long> {
    List<ScalpPhoto> findAllByClientIdOrderByTakenAtDescCreatedAtDesc(Long clientId);
}
