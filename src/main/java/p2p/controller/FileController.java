package p2p.controller;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import p2p.handler.CORSHandler;
import p2p.handler.DownloadHandler;
import p2p.handler.UploadHandler;
import p2p.service.FileSharer;

/**
 * Controller bootstrapping the HTTP REST server and wiring handlers.
 */
public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "p2p-file-uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Attach modular HTTP route handlers
        server.createContext("/upload", new UploadHandler(uploadDir, fileSharer));
        server.createContext("/download", new DownloadHandler(fileSharer));
        server.createContext("/", new CORSHandler());
        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("API server started on port " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }
}
