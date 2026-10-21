package com.relay.backend.controller;

import com.relay.backend.dto.ChatDtos.*;
import com.relay.backend.exception.RateLimitExceededException;
import com.relay.backend.security.CurrentUser;
import com.relay.backend.service.ChatService;
import com.relay.backend.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/messages")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUser currentUser;
    private final RateLimiterService rateLimiter;

    public ChatController(ChatService chatService, CurrentUser currentUser, RateLimiterService rateLimiter) {
        this.chatService = chatService;
        this.currentUser = currentUser;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public List<MessageResponse> history(@PathVariable UUID sessionId) {
        return chatService.history(sessionId, currentUser.email());
    }

    @PostMapping
    public MessageResponse send(@PathVariable UUID sessionId, @Valid @RequestBody SendMessageRequest request) {
        String email = currentUser.email();
        checkRateLimit(email);
        return chatService.sendMessage(sessionId, email, request.content());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public StreamingResponseBody sendStream(@PathVariable UUID sessionId, @Valid @RequestBody SendMessageRequest request) {
        // Captured here, on the request thread, before handing off to the async
        // writer thread — the security context doesn't carry over automatically.
        String email = currentUser.email();
        checkRateLimit(email);

        return outputStream -> chatService.streamMessage(sessionId, email, request.content(), chunk -> {
            try {
                outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void checkRateLimit(String email) {
        if (!rateLimiter.tryConsume(email)) {
            throw new RateLimitExceededException(
                    "You're sending messages too quickly. Please wait a moment and try again."
            );
        }
    }
}
