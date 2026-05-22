package com.hairmony.warehouse.web.dto;

import com.hairmony.warehouse.domain.product.Unit;
import jakarta.validation.constraints.*;
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
    @Size(max = 150, message = "{validation.size.max150}")
    private String name;

    @Size(max = 100, message = "{validation.size.max100}")
    private String brand;

    @NotNull
    private com.hairmony.warehouse.domain.category.Category category;

    @Size(max = 50, message = "{validation.size.max50}")
    private String barcode;

    @NotNull
    private Unit unit;

    @NotNull
    @DecimalMin(value = "0.001", message = "{validation.unitSize.positive}")
    private BigDecimal unitSize;

    @NotNull
    @DecimalMin(value = "0", message = "{validation.minStockLevel.positive}")
    private BigDecimal minStockLevel;

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String description;

    @DecimalMin(value = "0.01", message = "{validation.price.positive}")
    private BigDecimal recommendedPrice;

    @Size(max = 500, message = "{validation.size.max500}")
    private String imageUrl;

    private boolean active;

    private LocalDateTime deactivatedAt;

    @Size(max = 200, message = "{validation.size.max200}")
    private String deactivationReason;

    private BigDecimal currentQuantity;
}
