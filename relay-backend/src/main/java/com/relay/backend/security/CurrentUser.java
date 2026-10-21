package com.relay.backend.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Small wrapper so controllers never touch SecurityContextHolder directly.
// This is also the seam where you'd swap in something richer than "just the email"
// if you later put roles or a user ID into the JWT claims.
@Component
public class CurrentUser {
    public String email() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
