package p2p.compression;

import p2p.utils.CompressionUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CompressionTest {

    @Test
    public void testDeflateStrategyNotNull() {
        assertNotNull(new DeflateCompressionStrategy());
    }

    @Test
    public void testGzipStrategyNotNull() {
        assertNotNull(new GzipCompressionStrategy());
    }

    @Test
    public void testDeflateCompressReducesSize() {
        DeflateCompressionStrategy s = new DeflateCompressionStrategy();
        byte[] text = "AAAAAABBBBBBCCCCCCDDDDDD".getBytes();
        byte[] compressed = s.compress(text, "t.txt");
        assertTrue(compressed.length < text.length, "Deflate should reduce repetitive text");
    }

    @Test
    public void testGzipCompressReducesSize() {
        GzipCompressionStrategy s = new GzipCompressionStrategy();
        byte[] text = "AAAAAABBBBBBCCCCCCDDDDDD".repeat(100).getBytes();
        byte[] compressed = s.compress(text, "t.txt");
        assertTrue(compressed.length < text.length, "Gzip should reduce repetitive text");
    }

    @Test
    public void testDeflateDecompressRestoresOriginal() {
        DeflateCompressionStrategy s = new DeflateCompressionStrategy();
        byte[] original = "Hello SwiftShare DEFLATE test data.".getBytes();
        byte[] compressed = s.compress(original, "t.txt");
        byte[] decompressed = s.decompress(compressed, "t.txt");
        assertArrayEquals(original, decompressed, "DEFLATE roundtrip must restore original");
    }

    @Test
    public void testGzipDecompressRestoresOriginal() {
        GzipCompressionStrategy s = new GzipCompressionStrategy();
        byte[] original = "Hello SwiftShare GZIP test data.".getBytes();
        byte[] compressed = s.compress(original, "t.txt");
        byte[] decompressed = s.decompress(compressed, "t.txt");
        assertArrayEquals(original, decompressed, "GZIP roundtrip must restore original");
    }

    @Test
    public void testDetectAlgorithmGzipMagicBytes() {
        GzipCompressionStrategy s = new GzipCompressionStrategy();
        byte[] original = "test".getBytes();
        byte[] gzipped = s.compress(original, "t");
        assertEquals("GZIP", CompressionUtils.detectAlgorithm(gzipped),
            "detectAlgorithm should return GZIP for GZIP data");
    }

    @Test
    public void testDetectAlgorithmDeflateDefaults() {
        DeflateCompressionStrategy s = new DeflateCompressionStrategy();
        byte[] original = "test".getBytes();
        byte[] deflated = s.compress(original, "t");
        assertEquals("DEFLATE", CompressionUtils.detectAlgorithm(deflated),
            "detectAlgorithm should return DEFLATE for DEFLATE data");
    }
}
