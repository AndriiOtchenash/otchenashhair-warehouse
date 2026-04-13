package com.hairmony.warehouse.service;

import com.hairmony.warehouse.repository.UserRepository;
import com.hairmony.warehouse.web.dto.ChangePasswordDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean changePassword(String username, ChangePasswordDto dto) {
        var user = userRepository.findByUsername(username)
            .orElseThrow();

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            return false; // wrong current password
        }
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            return false; // passwords don't match
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        return true; // dirty checking handles save
    }
}
