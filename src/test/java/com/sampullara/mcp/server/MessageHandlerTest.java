package com.sampullara.mcp.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sampullara.mcp.server.McpSessionManager.McpSession;
import com.sampullara.mcp.server.MessageHandler.ErrorCode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

class MessageHandlerTest {
    
    @Mock
    private McpSessionManager sessionManager;
    
    @Mock
    private HttpExchange exchange;
    
    @Mock
    private SseEmitter sseEmitter;
    
    private MessageHandler handler;
    private ObjectMapper objectMapper;
    private Headers headers;
    
    @BeforeEach
    void setUp() {
        try(var _ = MockitoAnnotations.openMocks(this)) {
            objectMapper = new ObjectMapper();
            handler = new MessageHandler(sessionManager, objectMapper);
            headers = new Headers();

            // Setup common mocks
            when(exchange.getRequestHeaders()).thenReturn(headers);
            when(exchange.getResponseHeaders()).thenReturn(new Headers());
            when(sessionManager.getSession(anyString())).thenReturn(
                    new McpSession("test-session", sseEmitter, System.currentTimeMillis())
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void testListResources() throws IOException {
        // Register a test resource
        handler.registerResource(new MessageHandler.Resource(
            "file:///test/example.txt",
            "Test File",
            "text/plain",
            "A test resource"
        ));
        
        // Create request
        String request = """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "method": "resources/list",
                "sessionId": "test-session"
            }
            """;
        
        setupExchange(request);
        handler.handle(exchange);
        
        String response = getResponse();
        JsonNode responseJson = objectMapper.readTree(response);
        
        assertTrue(responseJson.has("result"));
        JsonNode resources = responseJson.get("result").get("resources");
        assertNotNull(resources);
        assertEquals(1, resources.size());
        
        JsonNode resource = resources.get(0);
        assertEquals("file:///test/example.txt", resource.get("uri").asText());
        assertEquals("Test File", resource.get("name").asText());
    }
    
    @Test
    void testListTools() throws IOException {
        ObjectNode inputSchema = objectMapper.createObjectNode();
        ObjectNode properties = inputSchema.putObject("properties");
        ObjectNode paramProp = properties.putObject("param");
        paramProp.put("type", "string");
        
        handler.registerTool(new MessageHandler.Tool(
            "test_tool",
            "A test tool",
            inputSchema
        ));
        
        String request = """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "method": "tools/list",
                "sessionId": "test-session"
            }
            """;
        
        setupExchange(request);
        handler.handle(exchange);
        
        String response = getResponse();
        JsonNode responseJson = objectMapper.readTree(response);
        
        assertTrue(responseJson.has("result"));
        JsonNode tools = responseJson.get("result").get("tools");
        assertNotNull(tools);
        assertEquals(1, tools.size());
        
        JsonNode tool = tools.get(0);
        assertEquals("test_tool", tool.get("name").asText());
    }
    
    @Test
    void testInvalidMethod() throws IOException {
        String request = """
            {
                "jsonrpc": "2.0",
                "id": "1",
                "method": "invalid/method",
                "sessionId": "test-session"
            }
            """;
        
        setupExchange(request);
        handler.handle(exchange);
        
        String response = getResponse();
        JsonNode responseJson = objectMapper.readTree(response);
        
        assertTrue(responseJson.has("error"), "Response should have an error field");
        assertEquals(ErrorCode.METHOD_NOT_FOUND, responseJson.get("error").get("code").asInt());
    }
    
    private void setupExchange(String request) throws IOException {
        ByteArrayInputStream requestBody = new ByteArrayInputStream(
            request.getBytes(StandardCharsets.UTF_8)
        );
        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getRequestBody()).thenReturn(requestBody);
        headers.add("X-MCP-Session-ID", "test-session");
        
        // Setup response capture
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);
        doNothing().when(exchange).sendResponseHeaders(anyInt(), anyLong());
    }
    
    private String getResponse() {
        ByteArrayOutputStream responseBody = (ByteArrayOutputStream) exchange.getResponseBody();
        return responseBody.toString(StandardCharsets.UTF_8);
    }
}