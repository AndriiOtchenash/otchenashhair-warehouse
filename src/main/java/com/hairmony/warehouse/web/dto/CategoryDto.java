package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {
    private Long id;

    @NotBlank
    @Size(max = 100, message = "{validation.size.max100}")
    private String name;

    @Min(value = 0, message = "{validation.sortOrder.min}")
    private Integer sortOrder;
}
