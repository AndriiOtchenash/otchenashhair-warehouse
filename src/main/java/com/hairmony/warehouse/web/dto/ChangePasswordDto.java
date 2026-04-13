package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {
    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 6, message = "Пароль має бути не менше 6 символів")
    private String newPassword;

    @NotBlank
    private String confirmPassword;
}
