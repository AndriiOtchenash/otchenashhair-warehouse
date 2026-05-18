package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierDto {
    private Long id;

    @NotBlank
    @Size(max = 100, message = "{validation.size.max100}")
    private String name;

    @Size(max = 255, message = "{validation.size.max255}")
    private String contactInfo;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String notes;
}
