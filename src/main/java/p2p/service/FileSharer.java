package p2p.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import p2p.utils.UploadUtils;

// Manages ongoing file sharing sessions and socket connections
public class FileSharer {

    // Maps port -> local file path on the machine
    private final ConcurrentHashMap<Integer, String> availableFiles;

    // Maps port -> 6-digit access PIN
    private final ConcurrentHashMap<Integer, String> accessTokens;

    // 5 minute window for a receiver to connect before the session expires
    private static final int TIMEOUT_MS = 300000;

    public FileSharer() {
        this.availableFiles = new ConcurrentHashMap<>();
        this.accessTokens = new ConcurrentHashMap<>();
    }

    // Set up a new file for sharing, assigns a random free port and PIN
    public int offerFile(String filePath) {
        long timeout = 5000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeout) {
            int port = UploadUtils.generateCode();

            if (!availableFiles.containsKey(port)) {
                String pin = UploadUtils.generateSecurityPin();
                availableFiles.put(port, filePath);
                accessTokens.put(port, pin);
                return port;
            }
        }

        throw new RuntimeException("Could not find a free port for sharing in time.");
    }

    public boolean isPortActive(int port) {
        return availableFiles.containsKey(port);
    }

    public String getPinForPort(int port) {
        return accessTokens.get(port);
    }

    // Find the port that corresponds to a given PIN
    public Integer getPortByPin(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            return null;
        }
        for (Map.Entry<Integer, String> entry : accessTokens.entrySet()) {
            if (entry.getValue().equals(pin.trim())) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Verify whether the supplied PIN matches what we have for this port
    public boolean validatePin(int port, String pin) {
        if (pin == null || !accessTokens.containsKey(port)) {
            return false;
        }
        return accessTokens.get(port).equals(pin.trim());
    }

    // Remove temp file and clear maps once the file has been downloaded
    public void cleanupFileTransfer(int port) {
        String filePath = availableFiles.remove(port);
        accessTokens.remove(port);

        if (filePath != null) {
            File file = new File(filePath);
            if (file.exists()) {
                if (!file.delete()) {
                    System.err.println("Could not delete temporary file: " + filePath);
                }
            }
        }
    }

    // Listen on the assigned port and send the file when the receiver connects
    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        if (filePath == null) {
            System.err.println("No file found for port " + port);
            return;
        }

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(TIMEOUT_MS);
            System.out.println("Serving " + new File(filePath).getName() + " on port " + port);

            Socket clientSocket = serverSocket.accept();
            clientSocket.setSoTimeout(TIMEOUT_MS);
            System.out.println("Receiver connected from: " + clientSocket.getInetAddress());

            new Thread(new FileSenderTask(clientSocket, filePath)).start();
        } catch (java.net.SocketTimeoutException e) {
            // Nobody connected within the timeout window — clean up so the PIN can't be reused
            System.err.println("No receiver connected within timeout for port " + port + ", cleaning up.");
            cleanupFileTransfer(port);
        } catch (IOException e) {
            System.err.println("Socket error on port " + port + ": " + e.getMessage());
        }
    }

    // Worker to stream file bytes over the socket
    private static class FileSenderTask implements Runnable {
        private final Socket clientSocket;
        private final String filePath;

        public FileSenderTask(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run() {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                clientSocket.setSoTimeout(TIMEOUT_MS);
                OutputStream out = clientSocket.getOutputStream();

                String name = new File(filePath).getName();
                // Send the filename first so the downloader knows what to call it
                String header = "Filename: " + name + "\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));

                byte[] buffer = new byte[8192];
                int count;
                while ((count = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                out.flush();
                System.out.println("File transfer complete for " + name);
            } catch (Exception e) {
                System.err.println("Error sending file over socket: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
