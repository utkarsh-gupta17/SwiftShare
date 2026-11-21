package p2p.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * A streaming multipart/form-data parser that writes uploaded file directly to a destination stream/file,
 * keeping memory footprint minimal.
 */
public class StreamingMultipartParser {

    private final InputStream input;
    private final String boundary;

    public StreamingMultipartParser(InputStream input, String boundary) {
        this.input = input;
        this.boundary = boundary;
    }

    public static class ParseResult {
        public final String filename;
        public final String contentType;
        public final File tempFile;

        public ParseResult(String filename, String contentType, File tempFile) {
            this.filename = filename;
            this.contentType = contentType;
            this.tempFile = tempFile;
        }
    }

    /**
     * Parses the multipart body, streaming the first file field into targetFile.
     */
    public ParseResult parseTo(File targetFile) throws IOException {
        // Read headers until \r\n\r\n
        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
        byte[] delimiter = new byte[]{'\r', '\n', '\r', '\n'};
        int matchIdx = 0;
        int b;

        while ((b = input.read()) != -1) {
            headerBaos.write(b);
            if (b == delimiter[matchIdx]) {
                matchIdx++;
                if (matchIdx == delimiter.length) {
                    break;
                }
            } else {
                if (b == delimiter[0]) {
                    matchIdx = 1;
                } else {
                    matchIdx = 0;
                }
            }
        }

        if (matchIdx != delimiter.length) {
            return null; // Unexpected end of headers
        }

        String headers = headerBaos.toString(StandardCharsets.UTF_8);

        // Grab filename
        String filename = "unnamed-file";
        String filePrefix = "filename=\"";
        int nameStart = headers.indexOf(filePrefix);
        if (nameStart != -1) {
            nameStart += filePrefix.length();
            int nameEnd = headers.indexOf('"', nameStart);
            if (nameEnd != -1) {
                filename = headers.substring(nameStart, nameEnd);
            }
        }

        // Grab content type
        String contentType = "application/octet-stream";
        String typePrefix = "Content-Type:";
        int typeStart = headers.indexOf(typePrefix);
        if (typeStart != -1) {
            typeStart += typePrefix.length();
            int typeEnd = headers.indexOf("\r\n", typeStart);
            if (typeEnd != -1) {
                contentType = headers.substring(typeStart, typeEnd).trim();
            } else {
                contentType = headers.substring(typeStart).trim();
            }
        }

        // Stream the body until the boundary: \r\n--boundary
        byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            // We use a small circular/sliding buffer or a lookahead to avoid writing the boundary to the file
            int boundaryLen = boundaryBytes.length;
            byte[] buffer = new byte[8192];
            byte[] lookahead = new byte[boundaryLen];

            int lookaheadCount = 0;
            // Prime the lookahead
            while (lookaheadCount < boundaryLen) {
                int read = input.read(lookahead, lookaheadCount, boundaryLen - lookaheadCount);
                if (read == -1) break;
                lookaheadCount += read;
            }

            while (true) {
                // Check if lookahead matches boundary
                if (matchBoundary(lookahead, lookaheadCount, boundaryBytes)) {
                    // Reached boundary, stop writing
                    break;
                }

                // If not matching, we can emit the first byte of lookahead and read one new byte
                bos.write(lookahead[0]);

                // Shift lookahead left by 1
                System.arraycopy(lookahead, 1, lookahead, 0, lookaheadCount - 1);
                int nextByte = input.read();
                if (nextByte == -1) {
                    // EOF reached before cleanly matching boundary
                    break;
                }
                lookahead[lookaheadCount - 1] = (byte) nextByte;
            }
            bos.flush();
        }

        return new ParseResult(filename, contentType, targetFile);
    }

    private boolean matchBoundary(byte[] lookahead, int len, byte[] boundary) {
        if (len < boundary.length) return false;
        for (int i = 0; i < boundary.length; i++) {
            if (lookahead[i] != boundary[i]) return false;
        }
        return true;
    }
}
