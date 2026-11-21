package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import p2p.service.FileSharer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UploadHandlerMemoryTest {

    @Test
    public void testMemoryUsageOnLargeUpload() throws IOException {
        // We want to simulate a large upload.
        // The current implementation reads everything into memory.
        // We will provide an InputStream that generates 50MB of data.
        // 50MB is small enough to not crash the test but large enough to measure.

        FileSharer sharer = new FileSharer();
        UploadHandler handler = new UploadHandler("target/uploads", sharer);

        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        MockHttpExchange exchange = new MockHttpExchange(50 * 1024 * 1024);
        handler.handle(exchange);

        long peakMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long diff = peakMemory - initialMemory;

        System.out.println("Memory difference: " + (diff / 1024 / 1024) + " MB");

        // Expect memory usage to be low (streaming).
        // Currently it will be > 50MB, so this should FAIL.
        assertTrue(diff < 10 * 1024 * 1024, "Memory usage should be less than 10MB, but was " + (diff / 1024 / 1024) + " MB");
    }

    static class MockHttpExchange extends HttpExchange {
        private final long size;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        MockHttpExchange(long size) {
            this.size = size;
            String boundary = "----Boundary";
            requestHeaders.add("Content-Type", "multipart/form-data; boundary=" + boundary);
        }

        @Override
        public Headers getRequestHeaders() { return requestHeaders; }
        @Override
        public Headers getResponseHeaders() { return responseHeaders; }
        @Override
        public URI getRequestURI() { return URI.create("/upload"); }
        @Override
        public String getRequestMethod() { return "POST"; }
        @Override
        public InputStream getRequestBody() {
            // Generate a multipart body with a large file
            String boundary = "----Boundary";
            String header = "--" + boundary + "\r\n" +
                           "Content-Disposition: form-data; name=\"file\"; filename=\"large.bin\"\r\n" +
                           "Content-Type: application/octet-stream\r\n\r\n";
            String footer = "\r\n--" + boundary + "--\r\n";

            return new SequenceInputStream(
                new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)),
                new SequenceInputStream(
                    new ZeroInputStream(size),
                    new ByteArrayInputStream(footer.getBytes(StandardCharsets.UTF_8))
                )
            );
        }

        @Override
        public OutputStream getResponseBody() { return responseBody; }
        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {}
        @Override
        public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
        @Override
        public int getResponseCode() { return 200; }
        @Override
        public void close() {}
        @Override
        public InetSocketAddress getLocalAddress() { return null; }
        @Override
        public String getProtocol() { return "HTTP/1.1"; }
        @Override
        public Object getAttribute(String name) { return null; }
        @Override
        public void setAttribute(String name, Object value) {}
        @Override
        public void setStreams(InputStream i, OutputStream o) {}
        @Override
        public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override
        public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }

    static class ZeroInputStream extends InputStream {
        private final long size;
        private long count = 0;
        ZeroInputStream(long size) { this.size = size; }
        @Override
        public int read() {
            if (count >= size) return -1;
            count++;
            return 0;
        }
        @Override
        public int read(byte[] b, int off, int len) {
            if (count >= size) return -1;
            int toRead = (int) Math.min(len, size - count);
            for (int i = 0; i < toRead; i++) b[off + i] = 0;
            count += toRead;
            return toRead;
        }
    }
}
