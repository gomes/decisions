package io.github.gomes.decisions.provider;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local HTTP server for the SDK-based providers: the official SDKs use their own HTTP stack, so they are
 * tested against a real socket. Records the last request and replies with a canned body.
 */
final class StubServer implements AutoCloseable {

    private final HttpServer server;
    volatile String method;
    volatile String path;
    volatile String query;
    volatile String body;
    final Map<String, String> headers = new ConcurrentHashMap<>();
    private volatile int status = 200;
    private volatile String reply = "{}";

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().getPath();
            query = exchange.getRequestURI().getQuery();
            headers.clear();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.getFirst()));
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    StubServer reply(int status, String body) {
        this.status = status;
        this.reply = body;
        return this;
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    JsonNode json() {
        return JsonMapper.shared().readTree(body);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
