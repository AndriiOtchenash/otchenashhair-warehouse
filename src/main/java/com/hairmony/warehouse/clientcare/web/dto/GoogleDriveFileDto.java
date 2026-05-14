package com.hairmony.warehouse.clientcare.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class GoogleDriveFileDto {
    private String fileId;
    private String name;
    private String mimeType;
    private String thumbnailUrl;
    private String webViewLink;
    private LocalDate createdTime;
}
