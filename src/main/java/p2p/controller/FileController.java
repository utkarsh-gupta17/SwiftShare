package main.java.p2p.controller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import main.java.p2p.service.FileSharer;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir; // server uses this to store the file temporarily, once the file has been uploaded by the client and is yet to be sent to someone
    // client --> server --> (uploadDir) --> send file to someone else
    private final ExecutorService executorService; // this handles multiple requests via threads for each client request


    public FileController(int port) throws IOException {
        this.fileSharer = new FileSharer();
        // creates a socket at the port given by the caller
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        // path of the tmp dir to store file temporarily
        this.uploadDir = System.getProperty("java.io.tmpdir") + File.separator + "p2p-file-uploads";
        this.executorService = Executors.newFixedThreadPool(10); // atmax 10 clients can be handled simulatneously

        File uploadDirFile = new File(uploadDir);
        if(!uploadDirFile.exists()) {
            uploadDirFile.mkdirs();
        }

        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());
        server.setExecutor(executorService);
    }

    public void start() {
        server.start();
        System.out.println("API server started on port "+ server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("API server stopped");
    }

    private class CORSHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            // we are adding headers to ensure that any client can connect to our server
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add(
                "Access-Control-Allow-Headers",
                "Content-Type,Authorization"
            );

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                // if someone makes a options request as CORSHandler is applicable on options
                exchange.sendResponseHeaders(204, -1); //send NO CONTENT
                return;
            }

            // GET, POST is handled by someone else, not by CORSHandler
            String response = "Not Found";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static class MultipartParser {

        private final byte[] data;
        private final String boundary;

        public MultipartParser(byte[] data, String boundary) {
            this.data = data;
            this.boundary = boundary;
        }

        public ParseResult parse() {
            try {
                // single \r\n seperates individual header lines
                // \r\n\r\n signifies the end of headers and start of content
                byte[] headerSeparator = { 13, 10, 13, 10 }; // \r\n\r\n
                int headerEndIndex = findSequence(data, headerSeparator, 0);
                if (headerEndIndex == -1) {
                    return null;
                }
                int contentStartIndex = headerEndIndex + headerSeparator.length;

                String headers = new String(data, 0, headerEndIndex);

                byte[] boundarySeparator =
                    ("\r\n--" + this.boundary).getBytes();
                int contentEndIndex = findSequence(
                    data,
                    boundarySeparator,
                    contentStartIndex
                );

                if (contentEndIndex == -1) {
                    boundarySeparator = ("\r\n--" +
                        this.boundary +
                        "--").getBytes();
                    contentEndIndex = findSequence(
                        data,
                        boundarySeparator,
                        contentStartIndex
                    );
                }

                if (contentEndIndex == -1) {
                    return null;
                }

                // extracting fileContent
                byte[] fileContent = Arrays.copyOfRange(
                    data,
                    contentStartIndex,
                    contentEndIndex
                );

                // extracting the filename from the request data
                String filename = "unnamed-file";
                String filenameMarker = "filename=\""; // we are finding this exact phrase's starting index
                // and then simply loop to get the filename
                // the filename is contained btwn quotes ("") hence filenameMarker contains one quote atlast and we end at the subsequent quote
                int filenameStart = headers.indexOf(filenameMarker);
                if (filenameStart != -1) {
                    filenameStart += filenameMarker.length();
                    int filenameEnd = headers.indexOf("\"", filenameStart);
                    if (filenameEnd != -1) {
                        filename = headers.substring(
                            filenameStart,
                            filenameEnd
                        );
                    }
                }

                // extracting the contentType from the request data
                String contentType = "application/octet-stream";
                String contentTypeMarker = "Content-Type: ";
                int contentTypeStart = headers.indexOf(contentTypeMarker);
                if (contentTypeStart != -1) {
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = headers.indexOf(
                        "\r\n", // this is how the contentType value ends in the header
                        contentTypeStart
                    );
                    if (contentTypeEnd != -1) {
                        contentType = headers.substring(
                            contentTypeStart,
                            contentTypeEnd
                        );
                    }
                }

                return new ParseResult(filename, contentType, fileContent);
            } catch (Exception e) {
                System.err.println(
                    "Error parsing multipart data: " + e.getMessage()
                );
                e.printStackTrace(); // Keeping this for debugging
                return null;
            }
        }

        private int findSequence(byte[] data, byte[] sequence, int startPos) {
            outer: 
            // sequence.length is the boundary here
            for (int i = startPos; i <= data.length - sequence.length; i++) {
                for (int j = 0; j < sequence.length; j++) {
                    if (data[i + j] != sequence[j]) {
                        continue outer; // recursive way of iterating over the content
                    }
                }
                return i;
            }
            return -1;
        }

        // the output class of what we will be sending back as result
        public static class ParseResult {

            public final String filename;
            public final String contentType;
            public final byte[] fileContent;

            public ParseResult(
                String filename,
                String contentType,
                byte[] fileContent
            ) {
                this.filename = filename;
                this.contentType = contentType;
                this.fileContent = fileContent;
            }
        }
    }

    private class UploadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // whenever a client uploads a file, we send a response with header
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*"); // to handle CORS

            // only POST is allowed here, fail fast
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length); // Method Not Allowed
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type"); // checking this in the header sent by client request

            if (
                contentType == null ||
                !contentType.startsWith("multipart/form-data")
            ) {
                // we return with a bad request as response
                String response =
                    "Bad Request: Content-Type must be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            /* this is how a sample POST response looks like with headers, this is what we need to parse
                POST /api/upload HTTP/1.1
                Host: localhost:8080
                Content-Type: multipart/form-data; boundary=----FileBoundary123
                Authorization: Bearer my-secret-token

                ------FileBoundary123
                Content-Disposition: form-data; name="file"; filename="example.pdf"
                Content-Type: text/plain

                hello world
                ------FileBoundary123-- 
            */

            // no issues so far, now we perform parsing
            try {
                // fetch the boundary, as this is what seperates the different sections
                String boundary = contentType.substring(
                    contentType.indexOf("boundary=") + 9 // length of the word "boundary="
                );

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(exchange.getRequestBody(), baos); // copy from request directly into byte array stream
                byte[] requestData = baos.toByteArray();

                MultipartParser parser = new MultipartParser(
                    requestData,
                    boundary
                ); //passing to parser
                MultipartParser.ParseResult result = parser.parse();

                if (result == null) {
                    String response =
                        "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(
                        400,
                        response.getBytes().length
                    );
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    return;
                }

                String filename = result.filename;
                if (filename == null || filename.trim().isEmpty()) {
                    filename = "unnamed-file"; // default filename ; incase we don't get filename from the header
                }

                // if already a unnamed-file exists in tempDir, we need to make sure to append something to make the file names unique
                String uniqueFilename =
                    UUID.randomUUID().toString() +
                    "_" +
                    new File(filename).getName();
                String filePath = uploadDir + File.separator + uniqueFilename;

                try (FileOutputStream fos = new FileOutputStream(filePath)) {
                    fos.write(result.fileContent); // as soon as the client uploads the file, we write the contents into the socket
                }

                // so far we have the file, we have written that file in the tempDir as well
                int port = fileSharer.offerFile(filePath); // opens a port for that file

                // starts a socket on that port and as soon as a client gets connected to that port, it sends the file in a new thread
                new Thread(() -> fileSharer.startFileServer(port)).start(); 


                String jsonResponse = "{\"port\": " + port + "}";
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(
                    200,
                    jsonResponse.getBytes().length
                );
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }
            } catch (Exception e) {
                System.err.println("Error processing file upload: " + e.getMessage());
                String response = "Server error: " + e.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    private class DownloadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");

            // only GET is allowed since client is fetching a file
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                String response = "Method Not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }

            String path = exchange.getRequestURI().getPath(); //contains port
            String portStr = path.substring(path.lastIndexOf('/') + 1);

            try {
                int port = Integer.parseInt(portStr);

                try (
                    Socket socket = new Socket("localhost", port);
                    // while getting the file from fileSharer, the server acts as the client and hence input stream is being used here
                    InputStream socketInput = socket.getInputStream()
                ) {
                    File tempFile = File.createTempFile("download-", ".tmp");
                    String filename = "downloaded-file"; // Default filename

                    // uploadHandler sends a file to filesharer, which filesharer keeps a socket open for until a client uses the downloadHandler to get
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        ByteArrayOutputStream headerBaos =
                            new ByteArrayOutputStream();
                        int b;
                        while ((b = socketInput.read()) != -1) {
                            if (b == '\n') break;
                            headerBaos.write(b);
                        }

                        String header = headerBaos.toString().trim();
                        if (header.startsWith("Filename: ")) {
                            filename = header.substring("Filename: ".length());
                        }

                        // we read the file at the port from filesharer into a temp file, which is what we send to the user afterwards
                        // this is the server keeping the content in a temp file
                        while ((bytesRead = socketInput.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }

                    headers.add(
                        "Content-Disposition",
                        "attachment; filename=\"" + filename + "\""
                    );
                    headers.add("Content-Type", "application/octet-stream");

                    exchange.sendResponseHeaders(200, tempFile.length());
                    try (
                        OutputStream os = exchange.getResponseBody();
                        FileInputStream fis = new FileInputStream(tempFile)
                    ) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        // now send the tempfile to the client from the server
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }

                    tempFile.delete();
                } catch (IOException e) {
                    System.err.println(
                        "Error downloading file from peer: " + e.getMessage()
                    );
                    String response =
                        "Error downloading file: " + e.getMessage();
                    headers.add("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(
                        500,
                        response.getBytes().length
                    );
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
            } catch (NumberFormatException e) {
                String response = "Bad Request: Invalid port number";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

}
