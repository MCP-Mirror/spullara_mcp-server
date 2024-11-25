package com.sampullara.mcp.server;

import java.util.concurrent.CountDownLatch;

public class SseEmitter {
    private final CountDownLatch latch = new CountDownLatch(1);

    public void emit(String event, String data) {
        // Implementation for sending SSE messages
    }

    public void complete() {
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }
}