package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.scalp.ScalpZone;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ScalpPhotoDto {
    private Long id;

    @NotNull
    private Long clientId;

    @NotBlank
    @Size(max = 500, message = "{validation.size.max500}")
    @Pattern(
        regexp = "https?://(drive|docs|photos)\\.google\\.com/.*",
        message = "{scalp.photo.error.invalidUrl}"
    )
    private String driveUrl;

    @NotNull
    @PastOrPresent
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate takenAt;

    @NotNull
    private ScalpZone zone;

    @Size(max = 1000, message = "{validation.size.max1000}")
    private String notes;
}
