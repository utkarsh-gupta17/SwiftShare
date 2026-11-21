package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import p2p.service.FileSharer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DownloadHandlerRateLimitTest {

    @Test
    public void testDownloadRateLimit() throws IOException {
        FileSharer sharer = new FileSharer();
        DownloadHandler handler = new DownloadHandler(sharer);

        // Simulate 10 requests which should be accepted or rejected by logic,
        // but the 11th should specifically trigger 429 Too Many Requests.
        for (int i = 0; i < 10; i++) {
            MockHttpExchange ex = new MockHttpExchange("1.2.3.4");
            handler.handle(ex);
            // It might return 403 or something because PIN is missing, but NOT 429
            assertEquals(false, ex.getResponseCode() == 429);
        }

        MockHttpExchange exLimit = new MockHttpExchange("1.2.3.4");
        handler.handle(exLimit);
        assertEquals(429, exLimit.getResponseCode(), "Should be rate limited on 11th request");
    }

    static class MockHttpExchange extends HttpExchange {
        private final String ip;
        private int responseCode = -1;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        MockHttpExchange(String ip) {
            this.ip = ip;
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/download?token=123456"); }
        @Override public String getRequestMethod() { return "GET"; }
        @Override public InputStream getRequestBody() { return InputStream.nullInputStream(); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress(ip, 12345); }
        @Override public int getResponseCode() { return responseCode; }
        @Override public void close() {}
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
        @Override public void setStreams(InputStream i, OutputStream o) {}
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
