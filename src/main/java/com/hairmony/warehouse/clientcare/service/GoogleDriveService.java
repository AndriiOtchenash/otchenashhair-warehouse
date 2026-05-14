package com.hairmony.warehouse.clientcare.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
public class GoogleDriveService {

    private static final String APPLICATION_NAME = "OtchenashHair Warehouse";
    private static final List<String> SCOPES = List.of(DriveScopes.DRIVE_READONLY);

    @Value("${google.service-account.json:}")
    private String serviceAccountJson;

    @Value("${google.drive.root-folder-id:}")
    private String rootFolderId;

    public boolean isRootFolderConfigured() {
        return rootFolderId != null && !rootFolderId.isBlank();
    }

    /**
     * Finds a subfolder by name inside the configured root folder.
     * Returns the subfolder ID, or null if not found.
     */
    public String findSubfolderIdByName(String clientName) {
        if (!isRootFolderConfigured()) {
            throw new DriveAccessException("Root folder not configured");
        }
        Drive drive = buildDriveClient();
        try {
            String safeName = clientName.replace("'", "\\'");
            String query = "'" + rootFolderId + "' in parents" +
                    " and name = '" + safeName + "'" +
                    " and mimeType = 'application/vnd.google-apps.folder'" +
                    " and trashed = false";

            FileList result = drive.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .execute();

            if (result.getFiles().isEmpty()) {
                log.debug("Drive subfolder not found for client '{}'", clientName);
                return null;
            }
            return result.getFiles().get(0).getId();

        } catch (DriveAccessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find subfolder '{}' in root folder: {}", clientName, e.getMessage());
            throw new DriveAccessException(e.getMessage());
        }
    }

    private Drive buildDriveClient() {
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("google.service-account.json is not configured");
            throw new DriveAccessException("Service account not configured");
        }
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(SCOPES);

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            log.error("Failed to build Drive client: {}", e.getMessage());
            throw new DriveAccessException(e.getMessage());
        }
    }

    public static class DriveAccessException extends RuntimeException {
        public DriveAccessException(String message) {
            super(message);
        }
    }
}
