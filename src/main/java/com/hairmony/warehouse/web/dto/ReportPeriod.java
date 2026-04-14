package com.hairmony.warehouse.web.dto;

import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReportPeriod {
    private LocalDate from;
    private LocalDate to;
    private String preset; // THIS_MONTH, LAST_MONTH, CUSTOM

    public static ReportPeriod thisMonth() {
        LocalDate now = LocalDate.now();
        return new ReportPeriod(now.withDayOfMonth(1), now, "THIS_MONTH");
    }
}
