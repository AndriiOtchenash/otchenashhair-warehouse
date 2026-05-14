package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientDto {
    private Long id;

    @NotBlank
    private String name;

    private String phone;
    private String notes;
    private String driveFolderUrl;
    private String driveFolderId;
}
