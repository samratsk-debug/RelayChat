package com.relay.backend.service;

import com.relay.backend.dto.ChatDtos.MessageResponse;
import com.relay.backend.model.ChatMessage;
import com.relay.backend.model.ChatSession;
import com.relay.backend.model.MessageRole;
import com.relay.backend.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String FALLBACK_REPLY = "Sorry, I couldn't reach the AI service just now. Please try again.";

    private final ChatMessageRepository messageRepository;
    private final SessionService sessionService;
    private final GeminiClient geminiClient;

    public ChatService(
            ChatMessageRepository messageRepository,
            SessionService sessionService,
            GeminiClient geminiClient
    ) {
        this.messageRepository = messageRepository;
        this.sessionService = sessionService;
        this.geminiClient = geminiClient;
    }

    public List<MessageResponse> history(UUID sessionId, String email) {
        ChatSession session = sessionService.requireSessionOwnedBy(sessionId, email);
        return messageRepository.findBySessionOrderByTimestampAsc(session).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Non-streaming send, kept as a fallback for simpler clients. */
    public MessageResponse sendMessage(UUID sessionId, String email, String content) {
        ChatSession session = sessionService.requireSessionOwnedBy(sessionId, email);
        List<GeminiClient.Turn> turns = buildTurns(session, content);
        boolean needsTitle = needsTitle(session, turns);

        String replyText;
        try {
            replyText = geminiClient.generateReply(turns);
        } catch (Exception e) {
            log.error("Gemini call failed for session {}", sessionId, e);
            replyText = FALLBACK_REPLY;
        }

        ChatMessage assistantMessage = messageRepository.save(
                new ChatMessage(session, MessageRole.ASSISTANT, replyText)
        );

        if (needsTitle) {
            generateAndSaveTitle(sessionId, email, content, replyText);
        }
        return toResponse(assistantMessage);
    }

    /**
     * Streams the reply through onChunk as it arrives, then saves the full
     * assistant message once streaming finishes (or the fallback text, on failure).
     */
    public void streamMessage(UUID sessionId, String email, String content, Consumer<String> onChunk) {
        ChatSession session = sessionService.requireSessionOwnedBy(sessionId, email);
        List<GeminiClient.Turn> turns = buildTurns(session, content);
        boolean needsTitle = needsTitle(session, turns);

        StringBuilder fullReply = new StringBuilder();
        try {
            geminiClient.streamReply(turns, chunk -> {
                fullReply.append(chunk);
                onChunk.accept(chunk);
            });
        } catch (Exception e) {
            log.error("Gemini streaming call failed for session {}", sessionId, e);
            if (fullReply.isEmpty()) {
                onChunk.accept(FALLBACK_REPLY);
                fullReply.append(FALLBACK_REPLY);
            }
            // If some text had already streamed before it broke, keep it: a partial
            // real answer beats a fallback message.
        }

        messageRepository.save(new ChatMessage(session, MessageRole.ASSISTANT, fullReply.toString()));

        // The reply is already on the user's screen; naming the chat happens after,
        // so it never delays the text appearing.
        if (needsTitle) {
            generateAndSaveTitle(sessionId, email, content, fullReply.toString());
        }
    }

    /** Saves the user's message and returns the full turn history (including it) for Gemini. */
    private List<GeminiClient.Turn> buildTurns(ChatSession session, String content) {
        List<ChatMessage> priorMessages = messageRepository.findBySessionOrderByTimestampAsc(session);
        messageRepository.save(new ChatMessage(session, MessageRole.USER, content));

        List<GeminiClient.Turn> turns = new ArrayList<>();
        for (ChatMessage m : priorMessages) {
            String role = m.getRole() == MessageRole.USER ? "user" : "model";
            turns.add(new GeminiClient.Turn(role, m.getContent()));
        }
        turns.add(new GeminiClient.Turn("user", content));
        return turns;
    }

    // Only name a chat on its very first message, and only if the user hasn't
    // already given it a title of their own.
    private boolean needsTitle(ChatSession session, List<GeminiClient.Turn> turns) {
        return SessionService.DEFAULT_TITLE.equals(session.getTitle()) && turns.size() == 1;
    }

    private void generateAndSaveTitle(UUID sessionId, String email, String userMessage, String reply) {
        String title = null;

        boolean replyUsable = !reply.isBlank() && !FALLBACK_REPLY.equals(reply);
        if (replyUsable) {
            try {
                String prompt = "Write a short title (at most 5 words) for a chat that starts with the exchange below. "
                        + "Reply with ONLY the title: no quotes, no markdown, no trailing punctuation.\n\n"
                        + "User: " + truncate(userMessage, 400) + "\n"
                        + "Assistant: " + truncate(reply, 300);
                String raw = geminiClient.generateReply(
                        List.of(new GeminiClient.Turn("user", prompt)),
                        "You write very short titles for chat conversations.");
                title = cleanTitle(raw);
            } catch (Exception e) {
                log.warn("Title generation failed for session {}", sessionId, e);
            }
        }

        if (title == null || title.isBlank()) {
            title = fallbackTitle(userMessage);
        }

        try {
            sessionService.updateSession(sessionId, email, title, null);
        } catch (Exception e) {
            log.warn("Could not save title for session {}", sessionId, e);
        }
    }

    // Models sometimes add quotes, asterisks or a full stop despite being told not to.
    private String cleanTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip().lines().findFirst().orElse("");
        t = t.replaceAll("[*_`#\"\u201C\u201D]", "").strip();
        t = t.replaceAll("[.!?:;,]+$", "").strip();
        if (t.length() > 60) {
            t = t.substring(0, 60).strip();
        }
        return t;
    }

    // Used when the AI title isn't available: the start of the user's first message.
    private String fallbackTitle(String userMessage) {
        String t = userMessage.strip().replaceAll("\\s+", " ");
        if (t.isEmpty()) {
            return SessionService.DEFAULT_TITLE;
        }
        return t.length() > 40 ? t.substring(0, 40).strip() + "\u2026" : t;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private MessageResponse toResponse(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getTimestamp()
        );
    }
}
