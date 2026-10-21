package com.relay.backend.controller;

import com.relay.backend.dto.SessionDtos.*;
import com.relay.backend.security.CurrentUser;
import com.relay.backend.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentUser currentUser;

    public SessionController(SessionService sessionService, CurrentUser currentUser) {
        this.sessionService = sessionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<SessionResponse> list() {
        return sessionService.listSessions(currentUser.email());
    }

    @PostMapping
    public SessionResponse create(@RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        return sessionService.createSession(currentUser.email(), title);
    }

    // PUT rather than PATCH so the existing CORS config (GET/POST/PUT/DELETE) works as is.
    // Send only the fields you want to change: {"title": "..."} and/or {"pinned": true}.
    @PutMapping("/{sessionId}")
    public SessionResponse update(@PathVariable UUID sessionId, @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.updateSession(sessionId, currentUser.email(), request.title(), request.pinned());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId) {
        sessionService.deleteSession(sessionId, currentUser.email());
        return ResponseEntity.noContent().build();
    }
}
