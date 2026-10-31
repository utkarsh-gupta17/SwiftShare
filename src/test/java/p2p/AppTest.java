package p2p;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import p2p.service.FileSharer;
import p2p.utils.MultipartParser;
import p2p.utils.UploadUtils;

public class AppTest {

    @Test
    public void testPortGenerationInRange() {
        for (int i = 0; i < 50; i++) {
            int port = UploadUtils.generateCode();
            assertTrue(port >= 49152 && port <= 65535, "Port must be in dynamic range 49152-65535");
        }
    }

    @Test
    public void testSecurityPinFormat() {
        for (int i = 0; i < 50; i++) {
            String pin = UploadUtils.generateSecurityPin();
            assertNotNull(pin);
            assertEquals(6, pin.length(), "PIN must be exactly 6 digits");
            int numeric = Integer.parseInt(pin);
            assertTrue(numeric >= 100000 && numeric <= 999999);
        }
    }

    @Test
    public void testFileSharerPinLifecycleAndCleanup() throws IOException {
        FileSharer sharer = new FileSharer();
        File temp = File.createTempFile("test-share-", ".txt");
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            fos.write("Hello P2P".getBytes(StandardCharsets.UTF_8));
        }

        int port = sharer.offerFile(temp.getAbsolutePath());
        assertTrue(sharer.isPortActive(port));

        String pin = sharer.getPinForPort(port);
        assertNotNull(pin);
        assertTrue(sharer.validatePin(port, pin));
        assertFalse(sharer.validatePin(port, "000000"));

        assertEquals(port, sharer.getPortByPin(pin));

        // Test cleanup
        sharer.cleanupFileTransfer(port);
        assertFalse(sharer.isPortActive(port));
        assertFalse(temp.exists(), "Staged file should be deleted on cleanup");
    }

    @Test
    public void testMultipartParsing() {
        String boundary = "TestBoundary123";
        String payloadString =
            "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"sample.txt\"\r\n" +
            "Content-Type: text/plain\r\n\r\n" +
            "Sample File Body Content\r\n" +
            "--" + boundary + "--\r\n";

        byte[] rawBytes = payloadString.getBytes(StandardCharsets.UTF_8);
        MultipartParser parser = new MultipartParser(rawBytes, boundary);
        MultipartParser.ParseResult result = parser.parse();

        assertNotNull(result);
        assertEquals("sample.txt", result.filename);
        assertEquals("text/plain", result.contentType);
        assertEquals("Sample File Body Content", new String(result.fileContent, StandardCharsets.UTF_8));
    }
}
