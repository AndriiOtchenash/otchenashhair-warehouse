package com.hairmony.warehouse.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDto {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 6, max = 72, message = "{profile.changePassword.sizeError}")
    private String newPassword;

    @NotBlank
    private String confirmPassword;

    @AssertTrue(message = "{profile.changePassword.passwordMismatch}")
    public boolean isPasswordsMatch() {
        if (newPassword == null || confirmPassword == null) return true; // let @NotBlank handle
        return newPassword.equals(confirmPassword);
    }
}
