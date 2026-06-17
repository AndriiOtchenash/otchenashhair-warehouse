package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.user.User;
import com.hairmony.warehouse.repository.UserRepository;
import com.hairmony.warehouse.web.dto.ChangePasswordDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    private static final String USERNAME     = "admin";
    private static final String STORED_HASH  = "$2a$10$hashedPassword";
    private static final String CORRECT_PASS = "correct";
    private static final String WRONG_PASS   = "wrong";
    private static final String NEW_PASS     = "newPass123";

    @BeforeEach
    void setUp() {
        User user = User.builder().username(USERNAME).password(STORED_HASH).build();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    @Test
    void wrongCurrentPassword_returnsFalse() {
        when(passwordEncoder.matches(WRONG_PASS, STORED_HASH)).thenReturn(false);

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword(WRONG_PASS);
        dto.setNewPassword(NEW_PASS);
        dto.setConfirmPassword(NEW_PASS);

        assertThat(userService.changePassword(USERNAME, dto)).isFalse();
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void confirmPasswordMismatch_returnsFalse() {
        when(passwordEncoder.matches(CORRECT_PASS, STORED_HASH)).thenReturn(true);

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword(CORRECT_PASS);
        dto.setNewPassword(NEW_PASS);
        dto.setConfirmPassword("different");

        assertThat(userService.changePassword(USERNAME, dto)).isFalse();
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void correctInput_encodesAndReturnsTrue() {
        when(passwordEncoder.matches(CORRECT_PASS, STORED_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASS)).thenReturn("$2a$newHash");

        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword(CORRECT_PASS);
        dto.setNewPassword(NEW_PASS);
        dto.setConfirmPassword(NEW_PASS);

        assertThat(userService.changePassword(USERNAME, dto)).isTrue();
        verify(passwordEncoder).encode(NEW_PASS);
    }
}
