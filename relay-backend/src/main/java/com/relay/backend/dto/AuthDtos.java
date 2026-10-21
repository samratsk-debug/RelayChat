package com.relay.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Grouped in one file since they're small and always used together.
public class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank
            @Pattern(
                    regexp = "^[A-Za-z0-9_]{3,20}$",
                    message = "Username must be 3 to 20 characters: letters, numbers or underscores"
            )
            String username,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password
    ) {}

    // identifier is an email or a username; usernames can't contain '@', so the two never clash.
    public record LoginRequest(
            @NotBlank(message = "Enter your email or username") String identifier,
            @NotBlank(message = "Enter your password") String password
    ) {}

    public record AuthResponse(
            String token,
            String email,
            String username // null for accounts created before usernames existed
    ) {}
}
