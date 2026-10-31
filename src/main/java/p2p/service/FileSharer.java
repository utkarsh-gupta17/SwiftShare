package main.java.p2p.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferOverflowException;
import java.util.HashMap;

import main.java.p2p.utils.UploadUtils;

public class FileSharer {
    
    // this stores the ports that are currently actively providing a file to download with the file path
    private HashMap<Integer, String> availableFiles;

    public FileSharer() {
        // basic initialisation
        availableFiles = new HashMap<>();
    }

    // public int offerFile(String filePath) {
    //     // loop and ensure you get a available port
    //     int port;
    //     while(true) {
    //         port = UploadUtils.generateCode();
    //         if(!availableFiles.containsKey(port)) {
    //             // this means this port is available to be added
    //             availableFiles.put(port,filePath);
    //             return port;
    //         }
    //     }
    // }

    public int offerFile(String filePath) {
        long timeout = 5000; // 5 seconds timout to avoid infinite looping
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeout) {
            int port = UploadUtils.generateCode();

            if (!availableFiles.containsKey(port)) {
                // this means this port is available to be added
                availableFiles.put(port, filePath);
                return port;
            }
        }

        throw new RuntimeException("Unable to generate an available port. Request timed out.");
    }

    public void startFileServer(int port) {
        String filePath = availableFiles.get(port);
        if(filePath==null) {
            System.out.println("No file is associated with port "+port);
            return;
        }

        // start a server and client Socket connection and spawn a thread which will serve the file
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serving file "+ new File(filePath).getName() + "on port "+port);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connection "+ clientSocket.getInetAddress());
            new Thread(new FileSharerHandler(clientSocket, filePath)).start();
        } catch (IOException e) {
            System.err.println("Error handling file server on port: "+port);
        } 
    }

    // Thread requires a class implementing Runnable which overrides the method run
    private static class FileSharerHandler implements Runnable {

        private final Socket clientSocket;
        private final String filePath;

        public FileSharerHandler(Socket clientSocket, String filePath) {
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }
    
        // here we upload the file to the clientSocket
        @Override
        public void run() {
            // Socket is a stream and in java every i/o is a stream as well
            try(FileInputStream fis = new FileInputStream(filePath)) {
                // sockets have input and output stream
                // input stream is coming through the socket
                // output stream is whatever you want to send to the socket
                // here we upload file to the clientSocket, so we use output stream

                OutputStream oos = clientSocket.getOutputStream();
                String fileName = new File(filePath).getName();
                // we first send the filename in the header
                String header = "Filename: "+fileName+"\n";
                oos.write(header.getBytes());

                byte[] buffer = new byte[4096]; //file limit
                int byteRead; // to read content from file
                while((byteRead = fis.read(buffer))!=-1) {
                    oos.write(buffer, 0, byteRead);
                    // next time the offset starts from byteRead, use this to expand file limit size
                }
                System.out.println("File " + fileName + " sent to " + clientSocket.getInetAddress());
                
            } catch (Exception e) {
                System.err.println("Error seding file to the client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (Exception e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        }
        
    }
}
