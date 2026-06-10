package com.hairmony.warehouse.clientcare.web.dto;

import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentDto {

    private Long id;

    // Existing client (nullable by design)
    private Long clientId;
    private String clientName;   // read-only, populated from entity
    private String clientPhone;  // read-only, populated from entity

    // Guest / unknown client
    @Size(max = 150, message = "{validation.size.max150}")
    private String guestName;

    @Size(max = 50, message = "{validation.size.max50}")
    private String guestPhone;

    @NotNull
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @NotNull
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    private AppointmentStatus status;

    @NotNull(message = "{appointment.service.required}")
    private Long serviceId;
    private String serviceName;  // read-only, populated from entity for display

    @Size(max = 2000, message = "{validation.size.max2000}")
    private String notes;

    /** Either an existing client or a guest name must be provided. */
    @AssertTrue(message = "{appointment.validation.clientOrGuest}")
    public boolean isClientOrGuestPresent() {
        return clientId != null || (guestName != null && !guestName.isBlank());
    }

    /** End time must be after start time. */
    @AssertTrue(message = "{appointment.error.endBeforeStart}")
    public boolean isEndAfterStart() {
        if (startAt == null || endAt == null) return true; // let @NotNull handle
        return endAt.isAfter(startAt);
    }

    /** Display name: existing client name takes priority over guest name. */
    public String getDisplayName() {
        return clientName != null ? clientName : guestName;
    }

    /** Display phone: existing client phone takes priority over guest phone. */
    public String getDisplayPhone() {
        return clientPhone != null ? clientPhone : guestPhone;
    }

    public boolean hasClient() {
        return clientId != null;
    }

    /** True when the appointment window has passed but status was never resolved. */
    public boolean isPastUnresolved() {
        return endAt != null
                && endAt.isBefore(LocalDateTime.now(ZoneId.of("Europe/Warsaw")))
                && Set.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED).contains(status);
    }
}
