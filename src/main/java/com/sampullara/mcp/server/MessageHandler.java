package com.sampullara.mcp.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class MessageHandler implements HttpHandler {
    private final McpSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final Map<String, Resource> resources = new HashMap<>();
    private final Map<String, Tool> tools = new HashMap<>();

    public record Resource(String uri, String name, String mimeType, String description) {}
    public record Tool(String name, String description, JsonNode inputSchema) {}

    public MessageHandler(McpSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    public void registerResource(Resource resource) {
        resources.put(resource.uri(), resource);
    }

    public void registerTool(Tool tool) {
        tools.put(tool.name(), tool);
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
        String method = message.get("method").asText();
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        
        if (message.has("id")) {
            response.put("id", message.get("id").asInt());
        }

        try {
            switch (method) {
                case "resources/list" -> {
                    ObjectNode result = response.putObject("result");
                    var resourceArray = result.putArray("resources");
                    resources.values().forEach(resource -> {
                        var resourceNode = resourceArray.addObject();
                        resourceNode.put("uri", resource.uri());
                        resourceNode.put("name", resource.name());
                        resourceNode.put("mimeType", resource.mimeType());
                        if (resource.description() != null) {
                            resourceNode.put("description", resource.description());
                        }
                    });
                }
                case "tools/list" -> {
                    ObjectNode result = response.putObject("result");
                    var toolsArray = result.putArray("tools");
                    tools.values().forEach(tool -> {
                        var toolNode = toolsArray.addObject();
                        toolNode.put("name", tool.name());
                        toolNode.put("description", tool.description());
                        toolNode.set("inputSchema", tool.inputSchema());
                    });
                }
                default -> {
                    ObjectNode error = response.putObject("error");
                    error.put("code", -32601);
                    error.put("message", "Method not found");
                }
            }
        } catch (Exception e) {
            ObjectNode error = response.putObject("error");
            error.put("code", -32000);
            error.put("message", "Internal error: " + e.getMessage());
        }

        return response;
    }
}
