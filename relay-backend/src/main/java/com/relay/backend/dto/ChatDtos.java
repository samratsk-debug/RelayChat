package com.relay.backend.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public class ChatDtos {

    public record SendMessageRequest(
            @NotBlank String content
    ) {}

    public record MessageResponse(
            UUID id,
            String role,
            String content,
            Instant timestamp
    ) {}
}
