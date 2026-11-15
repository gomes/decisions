package io.github.gomes.decisions.server;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.gomes.decisions.DecisionService;
import io.github.gomes.decisions.RequestCodec;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.spi.DecisionException;
import io.github.gomes.decisions.spi.DecisionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Executors;

/**
 * A small HTTP gateway exposing one neutral API in front of every configured decision provider.
 *
 * <ul>
 *   <li>{@code GET  /health}</li>
 *   <li>{@code GET  /v1/providers}</li>
 *   <li>{@code POST /v1/decisions?provider=<id>[&model=<model>]}</li>
 *   <li>{@code POST /v1/compare[?providers=a,b][&model.<id>=<model>]}</li>
 * </ul>
 *
 * If a gateway token is configured, every {@code /v1} call needs {@code Authorization: Bearer <token>}.
 */
public final class DecisionServer implements AutoCloseable {

    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private final DecisionService service;
    private final String token;
    private final HttpServer server;

    public DecisionServer(DecisionService service, String host, int port, String token) throws IOException {
        this.service = service;
        this.token = token == null || token.isBlank() ? null : token;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext("/", this::handle);
    }

    public DecisionServer start() {
        server.start();
        return this;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            try {
                if (path.equals("/health")) {
                    send(ex, 200, JsonHttp.MAPPER.createObjectNode().put("status", "ok"));
                    return;
                }
                if (path.startsWith("/v1/") && !authorized(ex)) {
                    send(ex, 401, error("missing or invalid bearer token"));
                    return;
                }
                Map<String, String> query = query(ex.getRequestURI().getRawQuery());
                switch (method + " " + path) {
                    case "GET /v1/providers" -> send(ex, 200, providers());
                    case "POST /v1/decisions" -> {
                        String provider = query.get("provider");
                        if (provider == null) {
                            send(ex, 400, error("query parameter 'provider' is required"));
                            return;
                        }
                        DecisionRequest req = RequestCodec.read(body(ex));
                        if (query.containsKey("model")) req = req.withModel(query.get("model"));
                        send(ex, 200, service.decide(provider, req));
                    }
                    case "POST /v1/compare" -> {
                        DecisionRequest req = RequestCodec.read(body(ex));
                        List<String> ids = query.containsKey("providers")
                                ? Arrays.stream(query.get("providers").split(",")).map(String::trim)
                                        .filter(s -> !s.isEmpty()).toList()
                                : List.of();
                        Map<String, String> models = new HashMap<>();
                        query.forEach((k, v) -> {
                            if (k.startsWith("model.")) models.put(k.substring(6), v);
                        });
                        send(ex, 200, service.compare(req, ids, models));
                    }
                    default -> send(ex, 404, error("no route for " + method + " " + path));
                }
            } catch (IllegalArgumentException e) {
                send(ex, 400, error(e.getMessage()));
            } catch (NoSuchElementException e) {
                send(ex, 404, error(e.getMessage()));
            } catch (DecisionException e) {
                ObjectNode err = error(e.getMessage());
                if (e.httpStatus() > 0) err.put("upstreamStatus", e.httpStatus());
                send(ex, e.httpStatus() > 0 ? 502 : 422, err);
            } catch (RuntimeException e) {
                send(ex, 500, error("internal error: " + e));
            }
        }
    }

    private boolean authorized(HttpExchange ex) {
        if (token == null) return true;
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(h.substring(7).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private ArrayNode providers() {
        ArrayNode arr = JsonHttp.MAPPER.createArrayNode();
        for (DecisionProvider p : service.registry().all()) {
            ObjectNode n = arr.addObject();
            n.put("id", p.id());
            n.put("name", p.displayName());
            n.put("defaultModel", p.defaultModel());
            n.put("maxQuestions", p.capabilities().maxQuestions());
            n.put("images", p.capabilities().images());
        }
        return arr;
    }

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("request body larger than " + MAX_BODY_BYTES + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    private static ObjectNode error(String message) {
        ObjectNode n = JsonHttp.MAPPER.createObjectNode();
        n.putObject("error").put("message", message);
        return n;
    }

    private static void send(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] bytes = JsonHttp.MAPPER.writeValueAsBytes(payload);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
