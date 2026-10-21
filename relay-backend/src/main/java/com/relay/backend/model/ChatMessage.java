package com.relay.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    public ChatMessage() {}

    public ChatMessage(ChatSession session, MessageRole role, String content) {
        this.session = session;
        this.role = role;
        this.content = content;
    }

    public UUID getId() { return id; }
    public ChatSession getSession() { return session; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public Instant getTimestamp() { return timestamp; }
}
