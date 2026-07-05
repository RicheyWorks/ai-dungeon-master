package com.xai.dungeonmaster.plugin.builtin.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Default {@link HttpTransport} backed by the JDK's {@link HttpClient}
 * (java.net.http, JDK 11+). The client is created lazily on first use so that
 * ServiceLoader-instantiated providers cost nothing until a request is actually
 * made. Thread-safe: {@link HttpClient} is immutable once built.
 */
public final class JdkHttpTransport implements HttpTransport {

    private final Duration timeout;
    private volatile HttpClient client;

    public JdkHttpTransport() {
        this(Duration.ofSeconds(30));
    }

    public JdkHttpTransport(Duration timeout) {
        this.timeout = (timeout != null) ? timeout : Duration.ofSeconds(30);
    }

    private HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = HttpClient.newBuilder().connectTimeout(timeout).build();
                    client = c;
                }
            }
        }
        return c;
    }

    @Override
    public Result post(String url, Map<String, String> headers, String jsonBody) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                b.header(h.getKey(), h.getValue());
            }
        }
        try {
            HttpResponse<String> res = client().send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(res.statusCode(), res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
}
