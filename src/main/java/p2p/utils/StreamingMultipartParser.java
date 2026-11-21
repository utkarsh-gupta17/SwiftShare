package p2p.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * A streaming multipart/form-data parser that writes uploaded file directly to a destination stream/file,
 * keeping memory footprint minimal and throughput high.
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
        int boundaryLen = boundaryBytes.length;

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos, 64 * 1024)) {

            byte[] buffer = new byte[64 * 1024];
            byte[] tail = new byte[boundaryLen];
            int tailLen = 0;

            // Fill initial tail
            while (tailLen < boundaryLen) {
                int read = input.read(tail, tailLen, boundaryLen - tailLen);
                if (read == -1) break;
                tailLen += read;
            }

            int n;
            while ((n = input.read(buffer)) != -1) {
                // We have 'tail' from previous chunk + 'buffer' with n bytes
                // The combined stream is: tail (tailLen bytes) followed by buffer (n bytes)
                // We can safely write everything except the last 'boundaryLen' bytes,
                // while checking if the boundary appears.

                // Fast path: search for boundary in (tail + buffer)
                byte[] combined = new byte[tailLen + n];
                System.arraycopy(tail, 0, combined, 0, tailLen);
                System.arraycopy(buffer, 0, combined, tailLen, n);

                int boundaryPos = findBoundary(combined, combined.length, boundaryBytes);
                if (boundaryPos != -1) {
                    // Boundary found! Write up to boundaryPos and stop
                    bos.write(combined, 0, boundaryPos);
                    bos.flush();
                    return new ParseResult(filename, contentType, targetFile);
                }

                // If not found, write out (combined.length - boundaryLen) bytes
                int safeToWrite = combined.length - boundaryLen;
                if (safeToWrite > 0) {
                    bos.write(combined, 0, safeToWrite);
                    System.arraycopy(combined, safeToWrite, tail, 0, boundaryLen);
                    tailLen = boundaryLen;
                } else {
                    System.arraycopy(combined, 0, tail, 0, combined.length);
                    tailLen = combined.length;
                }
            }

            // Check if boundary was at the very end
            if (tailLen >= boundaryLen && findBoundary(tail, tailLen, boundaryBytes) != -1) {
                int pos = findBoundary(tail, tailLen, boundaryBytes);
                bos.write(tail, 0, pos);
            } else if (tailLen > 0) {
                bos.write(tail, 0, tailLen);
            }
            bos.flush();
        }

        return new ParseResult(filename, contentType, targetFile);
    }

    private int findBoundary(byte[] src, int srcLen, byte[] boundary) {
        if (srcLen < boundary.length) return -1;
        outer:
        for (int i = 0; i <= srcLen - boundary.length; i++) {
            for (int j = 0; j < boundary.length; j++) {
                if (src[i + j] != boundary[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
