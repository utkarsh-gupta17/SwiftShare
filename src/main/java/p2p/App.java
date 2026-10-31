package p2p;

import java.io.IOException;
import p2p.controller.FileController;

/**
 * Main application entry point for the P2P File Share backend.
 *
 * Usage:
 *   java -cp ... p2p.App          # Interactive mode (waits for Enter to stop)
 *   java -cp ... p2p.App daemon   # Daemon mode (runs until killed/SIGTERM)
 */
public class App {
    private static volatile boolean running = true;

    public static void main(String[] args) {
        boolean daemonMode = args.length > 0 && "daemon".equalsIgnoreCase(args[0]);

        try {
            // Start the HTTP API server on port 8080
            FileController fileController = new FileController(8080);
            fileController.start();

            System.out.println("P2P File Share server started on port 8080");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                running = false;
                fileController.stop();
            }));

            if (daemonMode) {
                // Daemon mode: run until killed
                System.out.println("Running in daemon mode (use 'pm2 stop swiftshare-backend' to stop)");
                while (running) {
                    Thread.sleep(1000);
                }
            } else {
                // Interactive mode: wait for Enter key
                System.out.println("Press Enter to stop the server");
                System.in.read();
                fileController.stop();
            }

        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Server interrupted, shutting down...");
        }
    }
}
