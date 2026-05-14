package com.hairmony.warehouse.clientcare.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DriveFolderDto {

    private Long clientId;

    @NotBlank
    @Pattern(
        regexp = "https?://drive\\.google\\.com/.+",
        message = "{drive.folder.error.invalidUrl}"
    )
    private String driveFolderUrl;
}
