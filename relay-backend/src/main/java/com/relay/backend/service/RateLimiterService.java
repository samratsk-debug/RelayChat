package com.relay.backend.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// Keyed per-user (by email) so one person hammering the API doesn't affect anyone else.
// In-memory is fine for a single-instance learning project; a real multi-instance
// deployment would move this to Redis so limits are shared across servers.
@Service
public class RateLimiterService {

    private static final int MAX_REQUESTS_PER_WINDOW = 15; // matches Gemini's free-tier RPM limit
    private static final long WINDOW_MS = 60_000;

    private final Map<String, ConcurrentLinkedDeque<Long>> requestLog = new ConcurrentHashMap<>();

    public boolean tryConsume(String key) {
        long now = System.currentTimeMillis();
        ConcurrentLinkedDeque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
