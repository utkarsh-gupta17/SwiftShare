package p2p.handler;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles CORS headers and OPTIONS pre-flight checks across all endpoints.
 */
public class CORSHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.add("Access-Control-Allow-Origin", "*");
        responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        responseHeaders.add("Access-Control-Expose-Headers", "Content-Disposition, Content-Type, Content-Length");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        byte[] message = "Resource Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(404, message.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message);
        }
    }
}
