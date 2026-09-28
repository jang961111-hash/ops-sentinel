package com.opssentinel.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트 전용 가짜 OpenAI Chat Completions 서버(JDK 내장 HttpServer).
 *
 * <p>{@code openai.base-url}을 이 서버로 돌리면 실제 네트워크 호출 없이 AiSummaryService의
 * 실제 HTTP 경로를 그대로 탄다. {@link #setDelayMillis}로 응답 지연을 주입해 "AI가 느릴 때"
 * 락·트랜잭션 보유 시간이 어떻게 변하는지 재현한다.
 */
public final class FakeOpenAiServer implements AutoCloseable {

    public static final String SUMMARY = "가짜 AI 요약: 규칙엔진 판정에 따라 자동 조치가 실행되었습니다.";

    private final HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile long delayMillis;

    private FakeOpenAiServer(long delayMillis) throws IOException {
        this.delayMillis = delayMillis;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(this.delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = ("{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                    + SUMMARY + "\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        this.server.start();
    }

    public static FakeOpenAiServer start(long delayMillis) {
        try {
            return new FakeOpenAiServer(delayMillis);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public void setDelayMillis(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public int calls() {
        return calls.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
