package com.hairmony.warehouse.web.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductLookupDto {
    private Long id;
    private String name;
    private String brand;
    private String unit;
    private String barcode;
}
