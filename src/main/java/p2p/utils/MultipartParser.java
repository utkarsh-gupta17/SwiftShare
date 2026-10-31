package p2p.utils;

import java.nio.charset.StandardCharsets;

// Basic multipart parser for extracting file bytes and original filename
public class MultipartParser {

    private final byte[] data;
    private final String boundary;

    public MultipartParser(byte[] data, String boundary) {
        this.data = data;
        this.boundary = boundary;
    }

    public ParseResult parse() {
        try {
            if (data == null || boundary == null || data.length == 0) {
                return null;
            }

            // Headers end with standard double CRLF
            byte[] headerDelimiter = new byte[] { '\r', '\n', '\r', '\n' };
            int headerEnd = findSequence(data, headerDelimiter, 0);
            if (headerEnd == -1) {
                return null;
            }

            int contentStart = headerEnd + headerDelimiter.length;
            String headers = new String(data, 0, headerEnd, StandardCharsets.UTF_8);

            // Grab the original filename from Content-Disposition
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

            // Read the Content-Type header if provided
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

            // Match either the closing boundary or next part boundary
            byte[] closingBoundary = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
            byte[] normalBoundary = ("\r\n--" + boundary).getBytes(StandardCharsets.UTF_8);

            int contentEnd = findSequence(data, closingBoundary, contentStart);
            if (contentEnd == -1) {
                contentEnd = findSequence(data, normalBoundary, contentStart);
            }

            if (contentEnd == -1 || contentEnd <= contentStart) {
                return null;
            }

            // Copy out only the uploaded file bytes
            int length = contentEnd - contentStart;
            byte[] fileBytes = new byte[length];
            System.arraycopy(data, contentStart, fileBytes, 0, length);

            return new ParseResult(filename, contentType, fileBytes);
        } catch (Exception e) {
            System.err.println("Error while parsing multipart data: " + e.getMessage());
            return null;
        }
    }

    // Helper to find a sub-array sequence within the raw bytes
    private int findSequence(byte[] source, byte[] target, int startIndex) {
        if (target.length == 0 || startIndex + target.length > source.length) {
            return -1;
        }

        search:
        for (int i = startIndex; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    continue search;
                }
            }
            return i;
        }
        return -1;
    }

    public static class ParseResult {
        public final String filename;
        public final String contentType;
        public final byte[] fileContent;

        public ParseResult(String filename, String contentType, byte[] fileContent) {
            this.filename = filename;
            this.contentType = contentType;
            this.fileContent = fileContent;
        }
    }
}
