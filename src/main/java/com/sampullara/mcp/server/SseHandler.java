package com.sampullara.mcp.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SseHandler implements HttpHandler {
    private final McpSessionManager sessionManager;

    public SseHandler(McpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Set SSE headers
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");

        // Create new session
        var session = sessionManager.createSession();

        // Send initial message
        var response = """
            event: connected
            data: {"sessionId": "%s"}
            
            """.formatted(session.id());

        exchange.sendResponseHeaders(200, 0);
        try (var output = exchange.getResponseBody()) {
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();

            // Keep connection open
            session.emitter().await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sessionManager.removeSession(session.id());
        }
    }
}