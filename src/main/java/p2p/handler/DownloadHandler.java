package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import p2p.service.FileSharer;

// Handles GET requests for downloading files, checks PIN token and streams file
public class DownloadHandler implements HttpHandler {

    private final FileSharer fileSharer;
    // Match FileSharer's session window — 5 minutes for the download to complete
    private static final int SOCKET_TIMEOUT = 300000;

    public DownloadHandler(FileSharer fileSharer) {
        this.fileSharer = fileSharer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Access-Control-Expose-Headers", "Content-Disposition, Content-Type, Content-Length");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        // Read PIN from query string (?token=... or ?pin=...)
        String query = exchange.getRequestURI().getQuery();
        String token = getQueryParam(query, "token");
        if (token == null) {
            token = getQueryParam(query, "pin");
        }

        // Resolve port from URL path (/download/:port) or find port from the PIN
        Integer port = null;
        String path = exchange.getRequestURI().getPath();
        int slash = path.lastIndexOf('/');
        if (slash != -1 && slash < path.length() - 1) {
            try {
                int p = Integer.parseInt(path.substring(slash + 1));
                if (p > 0 && p <= 65535) {
                    port = p;
                }
            } catch (NumberFormatException ignored) {}
        }

        // If port wasn't in URL or was 0, find it via PIN
        if (port == null || port == 0) {
            port = fileSharer.getPortByPin(token);
        }

        // Check if the PIN is valid for this session
        if (port == null || !fileSharer.validatePin(port, token)) {
            sendResponse(exchange, 403, "Access Denied: Invalid or expired access PIN.");
            return;
        }

        // Connect to the internal server socket and fetch file
        File tempFile = null;
        try {
            tempFile = File.createTempFile("dl-temp-", ".tmp");
            tempFile.deleteOnExit();

            String filename = "downloaded-file";

            try (Socket socket = new Socket("localhost", port)) {
                socket.setSoTimeout(SOCKET_TIMEOUT);

                try (InputStream in = socket.getInputStream();
                     FileOutputStream out = new FileOutputStream(tempFile)) {

                    // Read first line for filename metadata
                    ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
                    int b;
                    while ((b = in.read()) != -1) {
                        if (b == '\n') break;
                        headerBytes.write(b);
                    }

                    String header = headerBytes.toString(StandardCharsets.UTF_8).trim();
                    if (header.startsWith("Filename: ")) {
                        filename = header.substring("Filename: ".length()).trim();
                    }

                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }
            }

            // Stream file back to client with download headers
            headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            headers.add("Content-Type", "application/octet-stream");

            exchange.sendResponseHeaders(200, tempFile.length());
            try (OutputStream httpOut = exchange.getResponseBody();
                 FileInputStream fileIn = new FileInputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = fileIn.read(buffer)) != -1) {
                    httpOut.write(buffer, 0, count);
                }
            }

            // Clean up file and remove session since download completed
            fileSharer.cleanupFileTransfer(port);

        } catch (IOException e) {
            System.err.println("Download error: " + e.getMessage());
            sendResponse(exchange, 500, "Error downloading file: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private String getQueryParam(String query, String name) {
        if (query == null || query.isBlank()) return null;
        String[] parts = query.split("&");
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx != -1) {
                if (part.substring(0, idx).trim().equalsIgnoreCase(name)) {
                    return part.substring(idx + 1).trim();
                }
            }
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
