package com.hireflow.auth.controller;

import com.hireflow.auth.dto.AuthResponse;
import com.hireflow.auth.dto.EmailVerificationConfirmRequest;
import com.hireflow.auth.dto.EmailVerificationRequest;
import com.hireflow.auth.dto.EmailVerificationResponse;
import com.hireflow.auth.dto.LoginRequest;
import com.hireflow.auth.dto.PasswordResetConfirmRequest;
import com.hireflow.auth.dto.PasswordResetRequest;
import com.hireflow.auth.dto.PasswordResetResponse;
import com.hireflow.auth.dto.RefreshRequest;
import com.hireflow.auth.dto.RegisterRequest;
import com.hireflow.auth.service.AuthService;
import com.hireflow.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/password-reset/request")
    public PasswordResetResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
    }

    @PostMapping("/email-verification/request")
    public EmailVerificationResponse requestEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        return authService.requestEmailVerification(request);
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailVerification(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        authService.confirmEmailVerification(request);
    }
}
