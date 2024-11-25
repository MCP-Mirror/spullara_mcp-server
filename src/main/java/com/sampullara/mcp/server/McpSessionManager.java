package com.sampullara.mcp.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class McpSessionManager {
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

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

    public void closeAllSessions() {
        sessions.values().forEach(session -> session.emitter().complete());
        sessions.clear();
    }
}
