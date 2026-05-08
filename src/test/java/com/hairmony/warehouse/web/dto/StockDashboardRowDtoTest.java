package com.hairmony.warehouse.web.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockDashboardRowDtoTest {

    @Test
    void status_ok_whenQuantityAboveMinStockLevel() {
        StockDashboardRowDto dto = StockDashboardRowDto.builder()
                .currentQuantity(new BigDecimal("15"))
                .minStockLevel(new BigDecimal("10"))
                .build();

        assertThat(dto.getStatus()).isEqualTo(StockDashboardRowDto.StockStatus.OK);
    }

    @Test
    void status_low_whenQuantityEqualsMinStockLevel() {
        StockDashboardRowDto dto = StockDashboardRowDto.builder()
                .currentQuantity(new BigDecimal("10"))
                .minStockLevel(new BigDecimal("10"))
                .build();

        assertThat(dto.getStatus()).isEqualTo(StockDashboardRowDto.StockStatus.LOW);
    }

    @Test
    void status_low_whenQuantityBelowMinStockLevelButAboveZero() {
        StockDashboardRowDto dto = StockDashboardRowDto.builder()
                .currentQuantity(new BigDecimal("3"))
                .minStockLevel(new BigDecimal("10"))
                .build();

        assertThat(dto.getStatus()).isEqualTo(StockDashboardRowDto.StockStatus.LOW);
    }

    @Test
    void status_out_whenQuantityIsZero() {
        StockDashboardRowDto dto = StockDashboardRowDto.builder()
                .currentQuantity(BigDecimal.ZERO)
                .minStockLevel(new BigDecimal("10"))
                .build();

        assertThat(dto.getStatus()).isEqualTo(StockDashboardRowDto.StockStatus.OUT);
    }

    @Test
    void status_out_whenQuantityIsExactlyZeroWithScale() {
        // BigDecimal("0.000") compareTo BigDecimal.ZERO == 0
        StockDashboardRowDto dto = StockDashboardRowDto.builder()
                .currentQuantity(new BigDecimal("0.000"))
                .minStockLevel(new BigDecimal("5"))
                .build();

        assertThat(dto.getStatus()).isEqualTo(StockDashboardRowDto.StockStatus.OUT);
    }
}
