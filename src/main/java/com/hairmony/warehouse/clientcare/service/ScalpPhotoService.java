package com.hairmony.warehouse.clientcare.service;

import com.hairmony.warehouse.clientcare.web.dto.ScalpPhotoDto;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.domain.scalp.ScalpPhoto;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.ScalpPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ScalpPhotoService {

    private static final Pattern DRIVE_FILE_ID_PATTERN =
            Pattern.compile("(?:/d/|[?&]id=)([a-zA-Z0-9_-]{15,})");

    private final ScalpPhotoRepository scalpPhotoRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public List<ScalpPhoto> findByClientId(Long clientId) {
        return scalpPhotoRepository.findAllByClientIdOrderByTakenAtDescCreatedAtDesc(clientId);
    }

    @Transactional(readOnly = true)
    public ScalpPhotoDto findById(Long id) {
        ScalpPhoto photo = scalpPhotoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("photo.notFound"));
        ScalpPhotoDto dto = new ScalpPhotoDto();
        dto.setId(photo.getId());
        dto.setClientId(photo.getClient().getId());
        dto.setDriveUrl(photo.getDriveUrl());
        dto.setTakenAt(photo.getTakenAt());
        dto.setZone(photo.getZone());
        dto.setNotes(photo.getNotes());
        return dto;
    }

    @Transactional
    public void update(Long id, ScalpPhotoDto dto) {
        ScalpPhoto photo = scalpPhotoRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("photo.notFound"));
        photo.setDriveUrl(dto.getDriveUrl().trim());
        photo.setDriveFileId(extractFileId(dto.getDriveUrl()));
        photo.setTakenAt(dto.getTakenAt());
        photo.setZone(dto.getZone());
        photo.setNotes(dto.getNotes());
    }

    @Transactional
    public void save(ScalpPhotoDto dto) {
        Client client = clientRepository.findById(dto.getClientId())
                .orElseThrow(() -> new IllegalStateException("client.notFound"));
        ScalpPhoto photo = ScalpPhoto.builder()
                .client(client)
                .driveUrl(dto.getDriveUrl().trim())
                .driveFileId(extractFileId(dto.getDriveUrl()))
                .takenAt(dto.getTakenAt())
                .zone(dto.getZone())
                .notes(dto.getNotes())
                .build();
        scalpPhotoRepository.save(photo);
    }

    @Transactional
    public void delete(Long id, Long clientId) {
        scalpPhotoRepository.findById(id).ifPresent(photo -> {
            if (photo.getClient().getId().equals(clientId)) {
                scalpPhotoRepository.delete(photo);
            }
        });
    }

    // Best-effort extraction — returns null if URL format is unrecognised
    private String extractFileId(String url) {
        if (url == null) return null;
        Matcher m = DRIVE_FILE_ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
