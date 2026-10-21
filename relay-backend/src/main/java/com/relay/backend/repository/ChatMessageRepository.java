package com.relay.backend.repository;

import com.relay.backend.model.ChatMessage;
import com.relay.backend.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionOrderByTimestampAsc(ChatSession session);

    // Must run inside a transaction (SessionService.deleteSession provides one).
    void deleteBySession(ChatSession session);
}
