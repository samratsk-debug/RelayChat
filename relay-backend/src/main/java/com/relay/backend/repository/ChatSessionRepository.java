package com.relay.backend.repository;

import com.relay.backend.model.ChatSession;
import com.relay.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    // Pinned chats first, then newest first.
    List<ChatSession> findByUserOrderByPinnedDescCreatedAtDesc(User user);
}
