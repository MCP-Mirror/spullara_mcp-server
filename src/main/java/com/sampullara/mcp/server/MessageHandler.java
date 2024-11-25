package com.sampullara.mcp.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class MessageHandler implements HttpHandler {
    private final McpSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public MessageHandler(
            McpSessionManager sessionManager,
            ObjectMapper objectMapper
    ) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        // Get session ID from header
        String sessionId = exchange.getRequestHeaders()
                .getFirst("X-MCP-Session-ID");

        if (sessionId == null) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        var session = sessionManager.getSession(sessionId);
        if (session == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        // Read request body
        String requestBody = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        // Parse JSON-RPC message
        var message = objectMapper.readTree(requestBody);

        // Handle message based on method
        var response = handleMessage(message);

        // Send response
        byte[] responseBytes = objectMapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(responseBytes);
        }
    }

    private JsonNode handleMessage(JsonNode message) {
        // Implement JSON-RPC method handling here
        // This would include initialize, resources/list, tools/call, etc.
        return null; // Placeholder
    }
}