package com.relay.backend.repository;

import com.relay.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    // Exact match: the JWT subject is the stored email, so lookups by token use this.
    Optional<User> findByEmail(String email);

    // Case-insensitive: used at login/register so "Sam@x.com" and "sam@x.com" match.
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
}
