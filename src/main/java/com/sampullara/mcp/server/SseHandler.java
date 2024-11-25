package com.sampullara.mcp.server;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class SseHandler implements HttpHandler {
    private final McpSessionManager sessionManager;
    private static final int KEEP_ALIVE_INTERVAL = 30000; // 30 seconds

    public SseHandler(McpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.getResponseHeaders().add("Allow", "GET");
            return;
        }

        // Set SSE headers
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache, no-transform");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.getResponseHeaders().add("X-Accel-Buffering", "no");
        
        // Create new session
        var session = sessionManager.createSession();
        
        // Set up the SSE emitter with the response output stream
        session.emitter().setOutputStream(exchange.getResponseBody());

        // Send response headers to start the SSE stream
        exchange.sendResponseHeaders(200, 0);

        // Start keep-alive timer
        CompletableFuture<Void> keepAlive = startKeepAlive(session.emitter());

        try {
            // Send initial connection message with session ID
            session.emitter().emit("connected", String.format("{\"sessionId\": \"%s\"}", session.id()));
            
            // Send initial retry interval
            session.emitter().emitRetry(KEEP_ALIVE_INTERVAL);
            
            // Keep connection open until client disconnects or session expires
            session.emitter().await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            try {
                session.emitter().emitError("Connection interrupted");
            } catch (IOException ignored) {
                // Ignore as we're closing anyway
            }
        } catch (IOException e) {
            System.err.println("Error sending SSE message: " + e.getMessage());
            try {
                session.emitter().emitError("Internal server error");
            } catch (IOException ignored) {
                // Ignore as we're closing anyway
            }
        } finally {
            try (exchange) {
                keepAlive.cancel(true);
                session.emitter().complete();
                sessionManager.removeSession(session.id());
            }
        }
    }

    private CompletableFuture<Void> startKeepAlive(SseEmitter emitter) {
        return CompletableFuture.runAsync(() -> {
            while (!emitter.isClosed()) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(KEEP_ALIVE_INTERVAL);
                    emitter.emit("ping", "{}");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    break;
                }
            }
        });
    }
}
