package com.hairmony.warehouse.clientcare.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalonServiceDto {

    private Long id;

    @NotBlank(message = "{service.name.required}")
    @Size(max = 150, message = "{validation.size.max150}")
    private String name;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String description;

    private boolean active;
}
