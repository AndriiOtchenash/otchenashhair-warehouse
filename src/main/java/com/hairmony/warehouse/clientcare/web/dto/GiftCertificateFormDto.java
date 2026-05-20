package com.hairmony.warehouse.clientcare.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class GiftCertificateFormDto {

    // Purchaser — existing client, free-text name, or salon gift (fromSalon=true)
    private boolean fromSalon;

    private Long purchaserClientId;

    @Size(max = 100, message = "{validation.size.max100}")
    private String purchaserName;

    @Size(max = 20, message = "{validation.phone.size}")
    @Pattern(regexp = "^[+0-9 ()\\-]*$", message = "{validation.phone.format}")
    private String purchaserPhone;

    @AssertTrue(message = "{gift.purchaser.required}")
    public boolean isPurchaserPresent() {
        return fromSalon
                || purchaserClientId != null
                || (purchaserName != null && !purchaserName.isBlank());
    }

    // Recipient — existing client (dropdown) OR free-text
    private Long recipientClientId;

    @Size(max = 100, message = "{validation.size.max100}")
    private String recipientName;

    @Size(max = 20, message = "{validation.phone.size}")
    @Pattern(regexp = "^[+0-9 ()\\-]*$", message = "{validation.phone.format}")
    private String recipientPhone;

    @NotNull(message = "{gift.service.required}")
    private Long serviceId;

    @NotNull(message = "{gift.expiresAt.required}")
    @Future(message = "{gift.expiresAt.future}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiresAt;

    @Size(max = 500, message = "{validation.size.max500}")
    private String notes;

    @AssertTrue(message = "{gift.recipient.required}")
    public boolean isRecipientPresent() {
        return recipientClientId != null
                || (recipientName != null && !recipientName.isBlank());
    }
}
