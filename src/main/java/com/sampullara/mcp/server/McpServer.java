package com.sampullara.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class McpServer {
    private final HttpServer server;
    private final ObjectMapper objectMapper;
    private final McpSessionManager sessionManager;
    private final McpCapabilities capabilities;

    public record McpCapabilities(
            boolean resources,
            boolean prompts,
            boolean tools,
            boolean sampling
    ) {}

    public McpServer(int port, McpCapabilities capabilities) throws Exception {
        this.objectMapper = new ObjectMapper();
        this.capabilities = capabilities;
        this.sessionManager = new McpSessionManager();

        // Create HTTP server with virtual threads
        ThreadFactory factory = Thread.ofVirtual()
                .name("mcp-worker-", 0)
                .factory();

        this.server = HttpServer.create(
                new InetSocketAddress(port),
                0
        );
        this.server.setExecutor(Executors.newThreadPerTaskExecutor(factory));

        // Set up endpoints
        setupEndpoints();
    }

    private void setupEndpoints() {
        // SSE endpoint for client connections
        server.createContext("/sse", new SseHandler(sessionManager));

        // Message endpoint for client-to-server communication
        server.createContext("/message", new MessageHandler(
                sessionManager,
                objectMapper
        ));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }
}