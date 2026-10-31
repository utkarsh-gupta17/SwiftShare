package p2p.compression;
import org.junit.Test;
import static org.junit.Assert.*;
public class CompressionTest {

    @Test
    public void testDeflateStrategyNotNull() { assertNotNull(new DeflateCompressionStrategy()); }
    @Test
    public void testGzipStrategyNotNull() { assertNotNull(new GzipCompressionStrategy()); }

    @Test
    public void testDeflateCompressReducesSize() {
        DeflateCompressionStrategy s = new DeflateCompressionStrategy();
        byte[] text = "AAAAAABBBBBBCCCCCCDDDDDD".getBytes();
        byte[] compressed = s.compress(text, "t.txt");
        assertTrue("Deflate should reduce repetitive text", compressed.length < text.length);
    }

    @Test
    public void testGzipCompressReducesSize() {
        GzipCompressionStrategy s = new GzipCompressionStrategy();
        byte[] text = "AAAAAABBBBBBCCCCCCDDDDDD".repeat(100).getBytes();
        byte[] compressed = s.compress(text, "t.txt");
        assertTrue("Gzip should reduce repetitive text", compressed.length < text.length);
    }

    @Test
    public void testDeflateDecompressRestoresOriginal() {
        DeflateCompressionStrategy s = new DeflateCompressionStrategy();
        byte[] original = "Hello SwiftShare DEFLATE test data.".getBytes();
        byte[] compressed = s.compress(original, "t.txt");
        byte[] decompressed = s.decompress(compressed, "t.txt");
        assertArrayEquals(original, decompressed);
    }

    @Test
    public void testGzipDecompressRestoresOriginal() {
        GzipCompressionStrategy s = new GzipCompressionStrategy();
        byte[] original = "Hello SwiftShare GZIP test data.".getBytes();
        byte[] compressed = s.compress(original, "t.txt");
        byte[] decompressed = s.decompress(compressed, "t.txt");
        assertArrayEquals(original, decompressed);
    }

    @Test
    public void testDetectAlgorithmGzipMagicBytes() {
        // GZIP magic = 0x1f 0x8b
        GzipCompressionStrategy s = new GzipCompressionStrategy();
        byte[] original = "test".getBytes();
        byte[] gzipped = s.compress(original, "t");
        assertEquals("GZIP", CompressionUtils.detectAlgorithm(gzipped));
    }

    @Test
    public void testDetectAlgorithmDeflateDefaults() {
        DeflateCompressionStrategy s = new DeflateCompressionStrategy();
        byte[] original = "test".getBytes();
        byte[] deflated = s.compress(original, "t");
        assertEquals("DEFLATE", CompressionUtils.detectAlgorithm(deflated));
    }
}
