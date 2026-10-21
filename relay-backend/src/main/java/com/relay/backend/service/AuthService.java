package com.relay.backend.service;

import com.relay.backend.dto.AuthDtos.*;
import com.relay.backend.model.User;
import com.relay.backend.repository.UserRepository;
import com.relay.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        String username = request.username().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("An account with that email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("That username is already taken");
        }

        User user = new User(email, username, passwordEncoder.encode(request.password()));
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.identifier().trim();

        // Usernames can't contain '@' (enforced at registration), so this is unambiguous.
        Optional<User> found = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier)
                : userRepository.findByUsernameIgnoreCase(identifier);

        // Same message whether the account or the password was wrong, so the
        // response doesn't reveal which usernames/emails exist.
        User user = found.orElseThrow(() -> new IllegalArgumentException("Incorrect email/username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect email/username or password");
        }

        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getUsername());
    }
}
