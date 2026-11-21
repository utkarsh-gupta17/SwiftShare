package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import p2p.service.FileSharer;
import p2p.utils.CompressionUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadHandlerStreamTest {

    @Test
    public void testDownloadStreamsWithDecompression() throws IOException {
        FileSharer sharer = new FileSharer();
        DownloadHandler handler = new DownloadHandler(sharer);

        // 50 KB of raw bytes — UploadHandler would have DEFLATE-compressed these before staging
        int originalSize = 50 * 1024;
        byte[] originalBytes = new byte[originalSize];
        byte[] compressedBytes = CompressionUtils.compress(originalBytes, "test-stream.bin");

        // Write the compressed bytes to the staged file, exactly as UploadHandler would
        File testFile = File.createTempFile("test-stream-", ".bin");
        testFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write(compressedBytes);
        }

        int port = sharer.offerFile(testFile.getAbsolutePath());
        String pin = sharer.getPinForPort(port);

        // Start file server in background
        new Thread(() -> sharer.startFileServer(port)).start();

        MockHttpExchange ex = new MockHttpExchange("127.0.0.1", pin, port);
        handler.handle(ex);

        assertEquals(200, ex.getResponseCode());
        // DownloadHandler decompresses before serving — client receives the original uncompressed size
        assertEquals(originalSize, ex.getResponseBodyBytes().length);
    }

    static class MockHttpExchange extends HttpExchange {
        private final String ip;
        private final String token;
        private final int port;
        private int responseCode = -1;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        MockHttpExchange(String ip, String token, int port) {
            this.ip = ip;
            this.token = token;
            this.port = port;
        }

        public byte[] getResponseBodyBytes() {
            return responseBody.toByteArray();
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/download/" + port + "?token=" + token); }
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
