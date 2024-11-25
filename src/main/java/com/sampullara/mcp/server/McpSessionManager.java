package com.sampullara.mcp.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class McpSessionManager {
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    public record McpSession(
            String id,
            SseEmitter emitter,
            long createdAt
    ) {}

    public McpSession createSession() {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter();
        McpSession session = new McpSession(id, emitter, System.currentTimeMillis());
        sessions.put(id, session);
        return session;
    }

    public McpSession getSession(String id) {
        return sessions.get(id);
    }

    public void removeSession(String id) {
        McpSession session = sessions.remove(id);
        if (session != null) {
            session.emitter().complete();
        }
    }

    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt() > SESSION_TIMEOUT_MS) {
                entry.getValue().emitter().complete();
                return true;
            }
            return false;
        });
    }

    public void closeAllSessions() {
        sessions.values().forEach(session -> session.emitter().complete());
        sessions.clear();
    }
}
