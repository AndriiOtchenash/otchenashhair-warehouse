package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.scalp.ScalpZone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ScalpPhotoDto {
    private Long id;

    @NotNull
    private Long clientId;

    @NotBlank
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

    private String notes;
}
