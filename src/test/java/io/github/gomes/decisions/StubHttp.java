package io.github.gomes.decisions;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.github.gomes.decisions.http.JsonHttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-process fake provider: records the last request and replies with a canned body. */
public final class StubHttp implements AutoCloseable {

    private final HttpServer server;
    public volatile String lastPath;
    public volatile JsonNode lastBody;
    public final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    private volatile int status = 200;
    private volatile String reply = "{}";

    public StubHttp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath = ex.getRequestURI().getPath();
            lastHeaders.clear();
            ex.getRequestHeaders().forEach((k, v) -> lastHeaders.put(k.toLowerCase(), v.get(0)));
            lastBody = JsonHttp.MAPPER.readTree(ex.getRequestBody());
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    public StubHttp reply(int status, String body) {
        this.status = status;
        this.reply = body;
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
