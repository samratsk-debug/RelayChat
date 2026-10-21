package com.relay.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String HOST = "https://generativelanguage.googleapis.com";

    // Temporary Google-side problems (overloaded, rate limited, brief outage) are
    // worth retrying; anything else (bad key, bad model name) would just fail again.
    private static final int MAX_ATTEMPTS = 3;
    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    private final RestClient restClient;
    private final HttpClient streamingHttpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;

    public GeminiClient(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model}") String model,
            @Value("${app.assistant.system-prompt:}") String systemPrompt,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create(HOST);
        this.streamingHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No Gemini API key configured (app.gemini.api-key / GEMINI_API_KEY). "
                    + "Login and chats will work, but AI replies will fail until you set one.");
        }
    }

    /** Non-streaming call using the normal chat persona. */
    public String generateReply(List<Turn> turns) {
        return generateReply(turns, chatSystemPrompt());
    }

    /** Non-streaming call with an explicit system prompt (null or blank means none). */
    public String generateReply(List<Turn> turns, String system) {
        Map<String, Object> body = buildBody(turns, system);

        GeminiResponse response;
        for (int attempt = 1; ; attempt++) {
            try {
                response = restClient.post()
                        .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GeminiResponse.class);
                break;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!RETRYABLE_STATUSES.contains(status) || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Gemini returned {} (attempt {} of {}); retrying", status, attempt, MAX_ATTEMPTS);
                pause(attempt);
            }
        }

        return extractText(response);
    }

    /**
     * Streams the reply, calling onChunk with each incremental piece of text as it
     * arrives from Gemini. Blocks the calling thread until the stream ends.
     *
     * Retries only cover failures that happen before any text has been sent. Once
     * chunks are flowing to the browser they can't be un-sent, so a mid-stream
     * failure is reported to the caller instead.
     */
    public void streamReply(List<Turn> turns, Consumer<String> onChunk) {
        try {
            String body = objectMapper.writeValueAsString(buildBody(turns, chatSystemPrompt()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HOST + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<Stream<String>> response;
            for (int attempt = 1; ; attempt++) {
                response = streamingHttpClient.send(request, BodyHandlers.ofLines());
                int status = response.statusCode();
                if (status < 400) {
                    break;
                }

                response.body().close();
                if (!RETRYABLE_STATUSES.contains(status) || attempt >= MAX_ATTEMPTS) {
                    throw new IllegalStateException("Gemini streaming call failed with status " + status);
                }
                log.warn("Gemini returned {} (attempt {} of {}); retrying", status, attempt, MAX_ATTEMPTS);
                pause(attempt);
            }

            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    if (!line.startsWith("data: ")) return; // skip blank lines / SSE comments

                    String json = line.substring(6);
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        JsonNode textNode = node.at("/candidates/0/content/parts/0/text");
                        if (!textNode.isMissingNode()) {
                            onChunk.accept(textNode.asText());
                        }
                    } catch (Exception parseError) {
                        log.warn("Skipping unparseable Gemini stream line: {}", line);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini streaming call was interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Gemini streaming call failed", e);
        }
    }

    // Waits about 1s after the first failure and about 2s after the second.
    private void pause(int attempt) {
        try {
            Thread.sleep(1000L * attempt + ThreadLocalRandom.current().nextLong(250));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Gemini", e);
        }
    }

    private Map<String, Object> buildBody(List<Turn> turns, String system) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", toContents(turns));
        if (system != null && !system.isBlank()) {
            body.put("system_instruction", Map.of("parts", List.of(Map.of("text", system))));
        }
        return body;
    }

    // The persona from application.yml, plus today's date (models don't know it
    // otherwise, which is how replies end up confidently naming the wrong year).
    private String chatSystemPrompt() {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return null;
        }
        return systemPrompt.strip() + "\n\nToday's date is " + LocalDate.now().format(DATE_FORMAT) + ".";
    }

    private List<Map<String, Object>> toContents(List<Turn> turns) {
        return turns.stream()
                .map(t -> Map.<String, Object>of(
                        "role", t.role(),
                        "parts", List.of(Map.of("text", t.text()))
                ))
                .toList();
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }
        List<Part> parts = response.candidates().get(0).content().parts();
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("Gemini returned no content parts");
        }
        return parts.get(0).text();
    }

    public record Turn(String role, String text) {}

    private record GeminiResponse(List<Candidate> candidates) {}
    private record Candidate(Content content) {}
    private record Content(List<Part> parts) {}
    private record Part(String text) {}
}
