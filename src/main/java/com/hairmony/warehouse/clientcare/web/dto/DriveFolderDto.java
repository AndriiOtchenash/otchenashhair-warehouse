package com.hairmony.warehouse.clientcare.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DriveFolderDto {

    @NotNull
    private Long clientId;

    @NotBlank
    @Size(max = 500, message = "{validation.size.max500}")
    @Pattern(
        regexp = "https?://drive\\.google\\.com/.+",
        message = "{drive.folder.error.invalidUrl}"
    )
    private String driveFolderUrl;
}
