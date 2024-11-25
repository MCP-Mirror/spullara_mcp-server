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

    // Add standard JSON-RPC error codes
    public static final class ErrorCode {
        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
    }

    private ObjectNode createJsonRpcResponse(String id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode createJsonRpcError(String id, int code, String message, JsonNode data) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", data);
        }
        
        return response;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            sendError(exchange, ErrorCode.INVALID_REQUEST, "Method not allowed", null);
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode request;
        
        try {
            request = objectMapper.readTree(body);
        } catch (Exception e) {
            sendError(exchange, ErrorCode.PARSE_ERROR, "Invalid JSON", null);
            return;
        }

        // Validate JSON-RPC 2.0 request format
        if (!isValidJsonRpcRequest(request)) {
            sendError(exchange, ErrorCode.INVALID_REQUEST, "Invalid JSON-RPC 2.0 request", null);
            return;
        }




        // Get session ID from header
        String sessionId = request.has("sessionId") ? request.get("sessionId").asText() : null;

        if (sessionId == null) {
            sendError(exchange, ErrorCode.INVALID_REQUEST, "Missing session ID", null);
            return;
        }

        var session = sessionManager.getSession(sessionId);
        if (session == null) {
            sendError(exchange, ErrorCode.INVALID_REQUEST, "Invalid session ID", null);
            return;
        }

        // Handle message based on method
        JsonNode response = handleMessage(request);

        // Send response
        if (response.has("error")) {
            sendError(exchange, response.get("error").get("code").asInt(),
                    response.get("error").get("message").asText(),
                    response.get("error").get("data"));
        } else {
            sendResponse(exchange, request.get("id").asText(), response.get("result"));
        }
    }

    private JsonNode handleMessage(JsonNode message) {
        String method = message.get("method").asText();
        String id = message.get("id").asText();

        try {
            return switch (method) {
                case "resources/list" -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    var resourceArray = objectMapper.createArrayNode();
                    resources.values().forEach(resource -> {
                        var resourceNode = objectMapper.createObjectNode();
                        resourceNode.put("uri", resource.uri());
                        resourceNode.put("name", resource.name());
                        resourceNode.put("mimeType", resource.mimeType());
                        if (resource.description() != null) {
                            resourceNode.put("description", resource.description());
                        }
                        resourceArray.add(resourceNode);
                    });
                    result.set("resources", resourceArray);
                    yield createJsonRpcResponse(id, result);
                }
                case "tools/list" -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    var toolsArray = objectMapper.createArrayNode();
                    tools.values().forEach(tool -> {
                        var toolNode = objectMapper.createObjectNode();
                        toolNode.put("name", tool.name());
                        toolNode.put("description", tool.description());
                        toolNode.set("inputSchema", tool.inputSchema());
                        toolsArray.add(toolNode);
                    });
                    result.set("tools", toolsArray);
                    yield createJsonRpcResponse(id, result);
                }
                default -> createJsonRpcError(id, ErrorCode.METHOD_NOT_FOUND, "Method not found", null);
            };
        } catch (Exception e) {
            return createJsonRpcError(id, ErrorCode.INTERNAL_ERROR, "Internal error: " + e.getMessage(), null);
        }
    }

    private boolean isValidJsonRpcRequest(JsonNode request) {
        return request.isObject() &&
               request.has("jsonrpc") &&
               request.get("jsonrpc").asText().equals("2.0") &&
               request.has("method") &&
               request.has("id");
    }

    private void sendError(HttpExchange exchange, int code, String message, JsonNode data) throws IOException {
        ObjectNode errorResponse = createJsonRpcError("null", code, message, data);  // Changed null to "null"
        byte[] responseBytes = objectMapper.writeValueAsBytes(errorResponse);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.getResponseBody().close();
    }

    private void sendResponse(HttpExchange exchange, String id, JsonNode result) throws IOException {
        ObjectNode response = createJsonRpcResponse(id, result);
        byte[] responseBytes = objectMapper.writeValueAsBytes(response);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.getResponseBody().close();
    }
}
