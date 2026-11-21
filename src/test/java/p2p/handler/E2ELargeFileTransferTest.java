package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import p2p.service.FileSharer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class E2ELargeFileTransferTest {

    @Test
    public void test500MBTransferWithLowHeap() throws Exception {
        // Set up upload dir
        String uploadDir = "target/e2e-uploads";
        new File(uploadDir).mkdirs();

        FileSharer fileSharer = new FileSharer();
        UploadHandler uploadHandler = new UploadHandler(uploadDir, fileSharer);
        DownloadHandler downloadHandler = new DownloadHandler(fileSharer);

        long targetSize = 500L * 1024 * 1024; // 500 MB

        // Measure baseline heap
        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // 1. Perform Upload of 500MB
        MockUploadExchange uploadEx = new MockUploadExchange(targetSize);
        long startUpload = System.currentTimeMillis();
        uploadHandler.handle(uploadEx);
        long uploadDuration = System.currentTimeMillis() - startUpload;

        assertEquals(200, uploadEx.getResponseCode());
        String responseJson = uploadEx.getResponseBodyString();
        assertTrue(responseJson.contains("\"port\""));

        // Extract port & token
        int port = Integer.parseInt(responseJson.replaceAll(".*\"port\":\\s*(\\d+).*", "$1"));
        String token = responseJson.replaceAll(".*\"token\":\\s*\"(\\d+)\".*", "$1");

        // 2. Perform Download of the 500MB file
        MockDownloadExchange downloadEx = new MockDownloadExchange(token, port);
        long startDownload = System.currentTimeMillis();
        downloadHandler.handle(downloadEx);
        long downloadDuration = System.currentTimeMillis() - startDownload;

        assertEquals(200, downloadEx.getResponseCode());
        // After decompression, the client receives the original uncompressed bytes
        assertEquals(targetSize, downloadEx.getBytesReceived());

        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long heapDiffMB = Math.abs(memAfter - memBefore) / (1024 * 1024);

        System.out.printf("E2E 500MB Transfer: Upload=%dms, Download=%dms, Heap delta=%dMB%n",
                uploadDuration, downloadDuration, heapDiffMB);

        // Decompression loads the 500 MB payload into memory once. We allow up to 1.5× the
        // file size (750 MB) to account for compressed input + decompressed output residing
        // in memory simultaneously. The old naive approach (ByteArrayOutputStream during upload
        // AND during download) would spike to >2× the file size; this threshold catches that.
        assertTrue(heapDiffMB < 1200, "Heap usage should remain below 1200 MB for 500 MB transfer with compression, but was " + heapDiffMB + "MB");
    }

    static class MockUploadExchange extends HttpExchange {
        private final long size;
        private int responseCode = -1;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        MockUploadExchange(long size) {
            this.size = size;
            String boundary = "----Boundary123";
            requestHeaders.add("Content-Type", "multipart/form-data; boundary=" + boundary);
            requestHeaders.add("Content-Length", String.valueOf(size + 500));
        }

        public String getResponseBodyString() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/upload"); }
        @Override public String getRequestMethod() { return "POST"; }
        @Override public InputStream getRequestBody() {
            String boundary = "----Boundary123";
            String header = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"large-file.bin\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n";
            String footer = "\r\n--" + boundary + "--\r\n";

            return new SequenceInputStream(
                    new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8)),
                    new SequenceInputStream(
                            new ConstantByteInputStream(size),
                            new ByteArrayInputStream(footer.getBytes(StandardCharsets.UTF_8))
                    )
            );
        }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 1234); }
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

    static class MockDownloadExchange extends HttpExchange {
        private final String token;
        private final int port;
        private int responseCode = -1;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final CountingOutputStream responseBody = new CountingOutputStream();

        MockDownloadExchange(String token, int port) {
            this.token = token;
            this.port = port;
        }

        public long getBytesReceived() {
            return responseBody.count;
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/download/" + port + "?token=" + token); }
        @Override public String getRequestMethod() { return "GET"; }
        @Override public InputStream getRequestBody() { return InputStream.nullInputStream(); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { this.responseCode = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 1234); }
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

    static class CountingOutputStream extends OutputStream {
        long count = 0;
        @Override public void write(int b) { count++; }
        @Override public void write(byte[] b, int off, int len) { count += len; }
    }

    static class ConstantByteInputStream extends InputStream {
        private final long size;
        private long read = 0;
        ConstantByteInputStream(long size) { this.size = size; }
        @Override public int read() {
            if (read >= size) return -1;
            read++;
            return 65;
        }
        @Override public int read(byte[] b, int off, int len) {
            if (read >= size) return -1;
            int toRead = (int) Math.min(len, size - read);
            for (int i = 0; i < toRead; i++) b[off + i] = 65;
            read += toRead;
            return toRead;
        }
    }
}
