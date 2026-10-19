package io.github.dakshshukla.lookalike.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Just enough of the OpenAI HTTP API (chat completions and embeddings) to drive the real Spring
 * AI client in tests. Embeddings are a fixed hash of the text. The chat endpoint cites the first
 * candidate ids it finds in the prompt; the first call can be told to cite an invented id so
 * the retry path gets exercised.
 */
public final class FakeOpenAiServer implements AutoCloseable {

    public static final int EMBEDDING_DIMENSIONS = 8;

    private static final Pattern CANDIDATE_ID = Pattern.compile("\\\\\"id\\\\\":\\\\\"(C-\\d+)\\\\\"");

    private final HttpServer server;
    private final boolean hallucinateFirstAnswer;
    private final AtomicInteger chatCalls = new AtomicInteger();
    private final AtomicInteger embeddingCalls = new AtomicInteger();
    private final List<String> chatBodies = new CopyOnWriteArrayList<>();

    public FakeOpenAiServer(boolean hallucinateFirstAnswer) throws IOException {
        this.hallucinateFirstAnswer = hallucinateFirstAnswer;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::chat);
        server.createContext("/v1/embeddings", this::embeddings);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int chatCalls() {
        return chatCalls.get();
    }

    public int embeddingCalls() {
        return embeddingCalls.get();
    }

    public List<String> chatBodies() {
        return chatBodies;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void chat(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatBodies.add(body);
        int call = chatCalls.incrementAndGet();

        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = CANDIDATE_ID.matcher(body);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        List<String> cited = new ArrayList<>(ids.stream().limit(3).toList());
        if (hallucinateFirstAnswer && call == 1 && !cited.isEmpty()) {
            cited.set(0, "C-9999");
        }

        StringBuilder items = new StringBuilder();
        for (int i = 0; i < cited.size(); i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"rank\":").append(i + 1)
                    .append(",\"summary\":\"Comparable past campaign ").append(cited.get(i)).append("\"")
                    .append(",\"citations\":[{\"campaignId\":\"").append(cited.get(i))
                    .append("\",\"reason\":\"Close in category and channel\"}]}");
        }
        String content = "{\"recommendations\":[" + items + "]}";
        String response = "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test-model\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":" + quote(content)
                + "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
        respond(exchange, response);
    }

    private void embeddings(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        embeddingCalls.incrementAndGet();
        // One vector per input string; the exact count is read from the request.
        int inputs = Math.max(1, countInputs(body));
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < inputs; i++) {
            if (i > 0) {
                data.append(',');
            }
            data.append("{\"object\":\"embedding\",\"index\":").append(i).append(",\"embedding\":[");
            for (int d = 0; d < EMBEDDING_DIMENSIONS; d++) {
                if (d > 0) {
                    data.append(',');
                }
                data.append(0.1 + ((body.hashCode() + i * 31 + d * 17) & 0xff) / 512.0);
            }
            data.append("]}");
        }
        respond(exchange, "{\"object\":\"list\",\"data\":[" + data + "],\"model\":\"test-embedding\","
                + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}");
    }

    private static int countInputs(String body) {
        int start = body.indexOf("\"input\"");
        if (start < 0) {
            return 1;
        }
        int open = body.indexOf('[', start);
        int close = body.indexOf(']', start);
        if (open < 0 || close < 0 || body.indexOf('"', start + 8) < open && open > start + 9) {
            return 1;
        }
        String array = body.substring(open + 1, close);
        return array.isBlank() ? 1 : (int) array.chars().filter(c -> c == '"').count() / 2;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
