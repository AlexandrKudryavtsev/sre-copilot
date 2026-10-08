package com.srecopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class OpenAiClientTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String RESULT = """
            {"status":"COMPLETED","probableCause":"Возможно, рост нагрузки.",
             "evidence":["CPU 78%"],"recommendations":["Проверить CPU"]}
            """;

    static AiProperties properties(String url, String token, Duration timeout, int limit) {
        return new AiProperties(token, "gpt-4o-mini", url, timeout, Duration.ofSeconds(1),
                Duration.ofMinutes(30), 120, 2000, limit, false);
    }
    private OpenAiClient client() {
        return new OpenAiClient(HttpClient.newHttpClient(), properties("https://api.openai.com/v1", "test-secret",
                Duration.ofSeconds(2), 1048576), json);
    }
    String envelope(String result) {
        return json.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message",
                "content", List.of(Map.of("type", "output_text", "text", result))))));
    }

    @Test void acceptsCompletedAndInsufficientData() {
        var result = client().parseResponse(envelope(RESULT));
        assertThat(result.status()).isEqualTo(AiDtos.Status.COMPLETED);
        assertThat(result.evidence()).containsExactly("CPU 78%");
        var insufficient = client().parseResponse(envelope("""
                {"status":"INSUFFICIENT_DATA","probableCause":null,"evidence":["Мало данных"],"recommendations":[]}
                """));
        assertThat(insufficient.status()).isEqualTo(AiDtos.Status.INSUFFICIENT_DATA);
        assertThat(insufficient.probableCause()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "{}", "[]", "null",
        "{\"status\":\"PENDING\",\"probableCause\":\"x\",\"evidence\":[\"x\"],\"recommendations\":[\"x\"]}",
        "{\"status\":\"COMPLETED\",\"probableCause\":null,\"evidence\":[\"x\"],\"recommendations\":[\"x\"]}",
        "{\"status\":\"COMPLETED\",\"probableCause\":\"x\",\"evidence\":[],\"recommendations\":[\"x\"]}",
        "{\"status\":\"COMPLETED\",\"probableCause\":\"x\",\"evidence\":[4],\"recommendations\":[\"x\"]}",
        "{\"status\":\"COMPLETED\",\"probableCause\":\"x\",\"evidence\":[\" \"],\"recommendations\":[\"x\"]}",
        "{\"status\":\"INSUFFICIENT_DATA\",\"probableCause\":null,\"evidence\":[],\"recommendations\":[]}"})
    void rejectsInvalidResult(String result) {
        assertThatThrownBy(() -> client().parseResponse(envelope(result))).isInstanceOf(AiAnalysisException.class)
                .hasMessage("OpenAI returned invalid analysis JSON.");
    }

    @Test void rejectsTrailingJsonDuplicateFieldsAndUnknownFields() {
        for (String malformed : List.of(RESULT + " {}", RESULT.replace("\"status\":\"COMPLETED\"",
                "\"status\":\"FAILED\",\"status\":\"COMPLETED\""),
                RESULT.replace("\"status\":", "\"extra\":true,\"status\":"))) {
            assertThatThrownBy(() -> client().parseResponse(envelope(malformed)))
                    .hasMessage("OpenAI returned invalid analysis JSON.");
        }
    }

    @Test void rejectsIncompleteAndRefusedResponses() {
        assertThatThrownBy(() -> client().parseResponse("{\"status\":\"incomplete\",\"output\":[]}"))
                .hasMessage("OpenAI returned an incomplete or failed response.");
        assertThatThrownBy(() -> client().parseResponse("""
                {"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"secret"}]}]}
                """)).hasMessage("OpenAI declined the analysis.");
    }

    @Test void sendsStructuredRequestWithBackendCredentialAndNoStorage() throws Exception {
        var captured = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = envelope(RESULT).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var client = new OpenAiClient(HttpClient.newHttpClient(), properties("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1", "test-secret", Duration.ofSeconds(2), 1048576), json);
            assertThat(client.analyze(context()).status()).isEqualTo(AiDtos.Status.COMPLETED);
            var request = json.readTree(captured.get());
            assertThat(auth.get()).isEqualTo("Bearer test-secret");
            assertThat(request.path("store").asBoolean()).isFalse();
            assertThat(request.path("text").path("format").path("strict").asBoolean()).isTrue();
            assertThat(json.readTree(request.path("input").asString()).path("incidentId").asInt()).isEqualTo(42);
            assertThat(captured.get()).doesNotContain("test-secret");
        } finally { server.stop(0); }
    }

    @Test void handlesHttpErrorsTimeoutAndOversizedResponsesWithoutLeakingBodies() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/error/responses", exchange -> {
            byte[] body = "test-secret upstream diagnostic".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/large/responses", exchange -> {
            byte[] body = new byte[2048]; exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/slow/responses", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try { Thread.sleep(300); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> new OpenAiClient(HttpClient.newHttpClient(), properties(base + "/error", "test-secret",
                    Duration.ofSeconds(2), 1048576), json).analyze(context())).hasMessage("OpenAI returned HTTP 429.");
            assertThatThrownBy(() -> new OpenAiClient(HttpClient.newHttpClient(), properties(base + "/large", "test-secret",
                    Duration.ofSeconds(2), 1024), json).analyze(context())).isInstanceOf(AiAnalysisException.class);
            assertThatThrownBy(() -> new OpenAiClient(HttpClient.newHttpClient(), properties(base + "/slow", "test-secret",
                    Duration.ofMillis(100), 1048576), json).analyze(context())).isInstanceOf(AiAnalysisException.class);
        } finally { server.stop(0); }
    }
    @Test void missingTokenFailsSafelyAndConfigurationToStringHidesIt() {
        assertThat(properties("https://api.openai.com/v1", "test-secret", Duration.ofSeconds(2), 1024).toString())
                .doesNotContain("test-secret");
        assertThatThrownBy(() -> new OpenAiClient(HttpClient.newHttpClient(), properties("https://api.openai.com/v1", "",
                Duration.ofSeconds(2), 1024), json).analyze(context())).hasMessage("OPENAI_TOKEN is not configured.");
    }
    private AiContextService.Context context() {
        return new AiContextService.Context(42, "Checkout", "CRITICAL", Instant.now(), Map.of("metric", "LATENCY"),
                new AiContextService.Signals(BigDecimal.valueOf(842), BigDecimal.valueOf(1248), BigDecimal.valueOf(3.24),
                        BigDecimal.valueOf(78)), List.of(), 0, false);
    }
}
