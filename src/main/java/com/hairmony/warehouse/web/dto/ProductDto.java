package com.hairmony.warehouse.web.dto;

import com.hairmony.warehouse.domain.product.Unit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {

    private Long id;

    @NotBlank
    private String name;

    private String brand;

    @NotNull
    private com.hairmony.warehouse.domain.category.Category category;

    private String barcode;

    @NotNull
    private Unit unit;

    @NotNull
    private BigDecimal unitSize;

    @NotNull
    private BigDecimal minStockLevel;

    private String description;

    private String imageUrl;

    private boolean active;

    private LocalDateTime deactivatedAt;

    private String deactivationReason;

    private java.math.BigDecimal currentQuantity;
}
