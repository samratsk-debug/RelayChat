package com.relay.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title = "New session";

    // columnDefinition gives existing rows a default when Hibernate adds this column.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean pinned = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public ChatSession() {}

    public ChatSession(User user, String title) {
        this.user = user;
        this.title = title;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public Instant getCreatedAt() { return createdAt; }
}
