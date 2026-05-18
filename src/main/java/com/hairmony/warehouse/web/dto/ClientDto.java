package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientDto {
    private Long id;

    @NotBlank
    @Size(max = 100, message = "{validation.size.max100}")
    private String name;

    @Size(max = 20, message = "{validation.phone.size}")
    @Pattern(regexp = "^[+0-9 ()\\-]*$", message = "{validation.phone.format}")
    private String phone;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String notes;

    private String driveFolderUrl;
    private String driveFolderId;
}
