package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import p2p.service.FileSharer;
import p2p.utils.MultipartParser;
import p2p.utils.CompressionUtils;

// Receives multipart upload, validates file type & size, and starts sharing session
public class UploadHandler implements HttpHandler {

    private final String uploadDir;
    private final FileSharer fileSharer;

    // Limit files to 500 MB max
    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;

    // Rate limit: 10 uploads per IP per minute
    private static final int MAX_UPLOADS_PER_MIN = 10;
    private static final long WINDOW_DURATION_MS = 60_000L;

    private static final ConcurrentHashMap<String, RateLimitEntry> ipRateLimits = new ConcurrentHashMap<>();

    // Common file types we allow
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".txt", ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp",
        ".zip", ".tar", ".gz", ".7z", ".doc", ".docx", ".xls", ".xlsx", ".csv",
        ".mp3", ".mp4", ".mkv", ".json", ".xml"
    );

    public UploadHandler(String uploadDir, FileSharer fileSharer) {
        this.uploadDir = uploadDir;
        this.fileSharer = fileSharer;
    }

    private static class RateLimitEntry {
        long windowStart;
        int count;

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        headers.add("Access-Control-Expose-Headers", "Content-Disposition, Content-Type, Content-Length");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendTextResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        // Check if this IP is making too many upload requests
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (checkRateLimit(clientIp)) {
            sendTextResponse(exchange, 429, "Too Many Requests: Please wait before uploading another file.");
            return;
        }

        // Validate multipart content type
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            sendTextResponse(exchange, 400, "Bad Request: Content-Type must be multipart/form-data");
            return;
        }

        String boundaryTag = "boundary=";
        int boundaryPos = contentType.indexOf(boundaryTag);
        if (boundaryPos == -1) {
            sendTextResponse(exchange, 400, "Bad Request: Missing multipart boundary");
            return;
        }
        String boundary = contentType.substring(boundaryPos + boundaryTag.length()).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }

        // Check declared Content-Length first if available
        String lengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (lengthHeader != null) {
            try {
                if (Long.parseLong(lengthHeader) > MAX_FILE_SIZE) {
                    sendTextResponse(exchange, 413, "Payload Too Large: File exceeds 500 MB limit");
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        // Read stream with running size check
        byte[] payloadBytes;
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8192];
            int read;
            long total = 0;

            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_SIZE) {
                    sendTextResponse(exchange, 413, "Payload Too Large: File exceeds 500 MB limit");
                    return;
                }
                baos.write(buffer, 0, read);
            }
            payloadBytes = baos.toByteArray();
        }

        // Parse file from form body
        MultipartParser parser = new MultipartParser(payloadBytes, boundary);
        MultipartParser.ParseResult parsed = parser.parse();

        if (parsed == null || parsed.fileContent == null || parsed.fileContent.length == 0) {
            sendTextResponse(exchange, 400, "Bad Request: No file data found");
            return;
        }

        String filename = cleanFilename(parsed.filename);
        if (!isAllowedType(filename)) {
            sendTextResponse(exchange, 415, "Unsupported File Type: Disallowed file extension");
            return;
        }

        // Save file to temp folder with a unique UUID prefix
        String uniqueName = UUID.randomUUID() + "_" + filename;
        String savePath = uploadDir + File.separator + uniqueName;

        // Compress file content before save
        byte[] compressed = CompressionUtils.compress(parsed.fileContent, filename);

        try (FileOutputStream fos = new FileOutputStream(savePath)) {
            fos.write(compressed);
        }

        // Stage file with sharer and start listening on socket
        int port = fileSharer.offerFile(savePath);
        String token = fileSharer.getPinForPort(port);

        new Thread(() -> fileSharer.startFileServer(port)).start();

        // Return JSON with the port number and the 6-digit access PIN
        String json = "{\"port\": " + port + ", \"token\": \"" + token + "\"}";
        headers.add("Content-Type", "application/json");
        byte[] resBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, resBytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(resBytes);
        }
    }

    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        RateLimitEntry entry = ipRateLimits.compute(ip, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > WINDOW_DURATION_MS) {
                return new RateLimitEntry(now);
            } else {
                existing.count++;
                return existing;
            }
        });

        return entry.count > MAX_UPLOADS_PER_MIN;
    }

    private boolean isAllowedType(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private String cleanFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "unnamed-file.txt";
        }
        return new File(name.trim()).getName();
    }

    private void sendTextResponse(HttpExchange exchange, int status, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }
}
