package com.relay.backend.service;

import com.relay.backend.dto.SessionDtos.SessionResponse;
import com.relay.backend.model.ChatSession;
import com.relay.backend.model.User;
import com.relay.backend.repository.ChatMessageRepository;
import com.relay.backend.repository.ChatSessionRepository;
import com.relay.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    public static final String DEFAULT_TITLE = "New session";

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    public SessionService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            UserRepository userRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    public List<SessionResponse> listSessions(String email) {
        User user = requireUser(email);
        return sessionRepository.findByUserOrderByPinnedDescCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    public SessionResponse createSession(String email, String title) {
        User user = requireUser(email);
        String finalTitle = (title == null || title.isBlank()) ? DEFAULT_TITLE : title.trim();
        return toResponse(sessionRepository.save(new ChatSession(user, finalTitle)));
    }

    /** Changes only the fields that are non-null: title (rename) and/or pinned. */
    public SessionResponse updateSession(UUID sessionId, String email, String title, Boolean pinned) {
        ChatSession session = requireSessionOwnedBy(sessionId, email);

        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Title can't be empty");
            }
            session.setTitle(trimmed);
        }
        if (pinned != null) {
            session.setPinned(pinned);
        }

        return toResponse(sessionRepository.save(session));
    }

    @Transactional
    public void deleteSession(UUID sessionId, String email) {
        ChatSession session = requireSessionOwnedBy(sessionId, email);

        // Messages reference the session, so they have to go first.
        messageRepository.deleteBySession(session);
        messageRepository.flush();
        sessionRepository.delete(session);
    }

    public ChatSession requireSessionOwnedBy(UUID sessionId, String email) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUser().getEmail().equals(email)) {
            throw new IllegalArgumentException("Session not found"); // don't leak existence to other users
        }
        return session;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private SessionResponse toResponse(ChatSession s) {
        return new SessionResponse(s.getId(), s.getTitle(), s.isPinned(), s.getCreatedAt());
    }
}
