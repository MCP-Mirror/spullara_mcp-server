package com.sampullara.mcp.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class SseEmitter {
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile OutputStream outputStream;
    private final Object outputStreamLock = new Object();
    private volatile boolean closed = false;

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public void emit(String event, String data) throws IOException {
        if (closed) {
            throw new IOException("SSE connection is closed");
        }
        
        if (outputStream == null) {
            throw new IllegalStateException("OutputStream not set");
        }

        // Format the SSE message according to the SSE specification
        StringBuilder message = new StringBuilder();
        if (event != null && !event.isEmpty()) {
            message.append("event: ").append(event).append("\n");
        }
        
        // Split data by newlines and prefix each line with "data: "
        String[] lines = data.split("\n");
        for (String line : lines) {
            message.append("data: ").append(line).append("\n");
        }
        message.append("\n"); // Empty line to terminate the message

        // Write the message to the output stream
        synchronized (outputStreamLock) {
            try {
                outputStream.write(message.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                closed = true;
                throw e;
            }
        }
    }

    public void emitError(String errorMessage) throws IOException {
        emit("error", errorMessage);
    }

    public void emitRetry(int milliseconds) throws IOException {
        if (closed) {
            throw new IOException("SSE connection is closed");
        }
        
        if (outputStream == null) {
            throw new IllegalStateException("OutputStream not set");
        }

        synchronized (outputStreamLock) {
            try {
                String message = "retry: " + milliseconds + "\n\n";
                outputStream.write(message.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (IOException e) {
                closed = true;
                throw e;
            }
        }
    }

    public void complete() {
        closed = true;
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                // Log error but don't throw as we're closing anyway
                System.err.println("Error closing SSE connection: " + e.getMessage());
            }
        }
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public boolean isClosed() {
        return closed;
    }
}