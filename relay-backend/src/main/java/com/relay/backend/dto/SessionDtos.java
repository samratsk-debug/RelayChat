package com.relay.backend.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class SessionDtos {

    public record SessionResponse(
            UUID id,
            String title,
            boolean pinned,
            Instant createdAt
    ) {}

    public record CreateSessionRequest(
            String title // optional; defaults to "New session" if blank
    ) {}

    // Either field may be omitted; only the ones that are present get changed.
    public record UpdateSessionRequest(
            @Size(max = 100, message = "Title can be at most 100 characters") String title,
            Boolean pinned
    ) {}
}
