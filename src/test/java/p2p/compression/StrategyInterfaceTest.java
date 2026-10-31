package p2p.compression;

import org.junit.Test;
import static org.junit.Assert.*;

public class StrategyInterfaceTest {

    private final DeflateCompressionStrategy deflate = new DeflateCompressionStrategy();
    private final GzipCompressionStrategy gzip = new GzipCompressionStrategy();

    // --- isSuitable thresholds ---

    @Test
    public void testDeflateSuitable_500B_tooSmall() {
        assertFalse(deflate.isSuitable(new byte[500], "f"));
    }

    @Test
    public void testDeflateSuitable_1KB_exactLowerBound() {
        assertTrue(deflate.isSuitable(new byte[1024], "f"));
    }

    @Test
    public void testDeflateSuitable_100KB_withinRange() {
        assertTrue(deflate.isSuitable(new byte[100 * 1024], "f"));
    }

    @Test
    public void testDeflateSuitable_1MB_exactUpperBound() {
        assertTrue(deflate.isSuitable(new byte[1024 * 1024], "f"));
    }

    @Test
    public void testDeflateSuitable_2MB_tooLarge() {
        assertFalse(deflate.isSuitable(new byte[2 * 1024 * 1024], "f"));
    }

    @Test
    public void testGzipSuitable_100KB_exactLowerBound() {
        assertTrue(gzip.isSuitable(new byte[100 * 1024], "f"));
    }

    @Test
    public void testGzipSuitable_50KB_tooSmall() {
        assertFalse(gzip.isSuitable(new byte[50 * 1024], "f"));
    }

    @Test
    public void testGzipSuitable_1MB_withinRange() {
        assertTrue(gzip.isSuitable(new byte[1024 * 1024], "f"));
    }

    @Test
    public void testGzipSuitable_5MB_stillSuitable() {
        assertTrue(gzip.isSuitable(new byte[5 * 1024 * 1024], "f"));
    }

    @Test
    public void testNullData_notSuitable() {
        assertFalse(deflate.isSuitable(null, "f"));
        assertFalse(gzip.isSuitable(null, "f"));
    }

    @Test
    public void testEmptyData_notSuitable() {
        assertFalse(deflate.isSuitable(new byte[0], "f"));
        assertFalse(gzip.isSuitable(new byte[0], "f"));
    }

    // --- Null/empty handling ---

    @Test
    public void testDeflateCompressNull_returnsNull() {
        byte[] r = deflate.compress(null, "x");
        assertNull(r);
    }

    @Test
    public void testGzipCompressNull_returnsNull() {
        byte[] r = gzip.compress(null, "x");
        assertNull(r);
    }

    @Test
    public void testDeflateDecompressNull_returnsEmptyArray() {
        byte[] r = deflate.decompress(null, "x");
        assertArrayEquals(new byte[0], r);
    }

    @Test
    public void testGzipDecompressNull_returnsEmptyArray() {
        byte[] r = gzip.decompress(null, "x");
        assertArrayEquals(new byte[0], r);
    }

    @Test
    public void testDeflateDecompressEmpty_returnsUnchanged() {
        byte[] empty = new byte[0];
        assertArrayEquals(empty, deflate.decompress(empty, "x"));
    }

    @Test
    public void testGzipDecompressEmpty_returnsUnchanged() {
        byte[] empty = new byte[0];
        assertArrayEquals(empty, gzip.decompress(empty, "x"));
    }

    // --- Decompress error: corrupt data returns empty array ---

    @Test
    public void testDeflateDecompressCorruptData_returnsEmptyArray() {
        byte[] corrupt = new byte[]{0x00, 0x01, (byte) 0xFF, 0x00};
        byte[] r = deflate.decompress(corrupt, "corrupt.bin");
        assertArrayEquals(new byte[0], r);
    }

    @Test
    public void testGzipDecompressCorruptData_returnsEmptyArray() {
        byte[] corrupt = new byte[]{0x00, 0x01, (byte) 0xFF, 0x00};
        byte[] r = gzip.decompress(corrupt, "corrupt.bin");
        assertArrayEquals(new byte[0], r);
    }

    // --- Correctness: compress + decompress restores original ---

    private void assertRoundtrip(CompressionStrategy s, byte[] original) {
        byte[] compressed = s.compress(original, "t");
        assertNotNull(compressed);
        byte[] decompressed = s.decompress(compressed, "t");
        assertArrayEquals("Roundtrip must restore exact original", original, decompressed);
    }

    @Test
    public void testDeflate_roundtrip_repetitiveText() {
        byte[] data = "AAAAAAAAAABBBBBBBBBBCCCCCCCCCC".getBytes();
        assertRoundtrip(deflate, data);
    }

    @Test
    public void testGzip_roundtrip_repetitiveText() {
        byte[] data = ("AAAAAAAAAABBBBBBBBBBCCCCCCCCCC" + "DDDDDDDDDD").getBytes();
        assertRoundtrip(gzip, data);
    }

    @Test
    public void testDeflate_roundtrip_utf8Text() {
        byte[] data = "Hello SwiftShare — compression test with UTF-8: café, 日本語, 🎉".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertRoundtrip(deflate, data);
    }

    @Test
    public void testGzip_roundtrip_utf8Text() {
        byte[] data = "Hello SwiftShare — compression test with UTF-8: café, 日本語, 🎉".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertRoundtrip(gzip, data);
    }

    @Test
    public void testDeflate_roundtrip_binaryData() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        assertRoundtrip(deflate, data);
    }

    @Test
    public void testGzip_roundtrip_binaryData() {
        byte[] data = new byte[512];
        for (int i = 0; i < 512; i++) data[i] = (byte) (i * 7);
        assertRoundtrip(gzip, data);
    }

    @Test
    public void testDeflate_roundtrip_allZeros() {
        byte[] data = new byte[4096];
        assertRoundtrip(deflate, data);
    }

    @Test
    public void testGzip_roundtrip_allZeros() {
        byte[] data = new byte[200 * 1024]; // large enough for GZIP
        assertRoundtrip(gzip, data);
    }

    @Test
    public void testDeflate_roundtrip_singleByte() {
        assertRoundtrip(deflate, new byte[]{42});
    }

    @Test
    public void testGzip_roundtrip_singleByte() {
        assertRoundtrip(gzip, new byte[]{42});
    }

    @Test
    public void testDeflate_roundtrip_1KB() {
        byte[] data = new byte[1024];
        for (int i = 0; i < 1024; i++) data[i] = (byte) (i % 256);
        assertRoundtrip(deflate, data);
    }

    @Test
    public void testGzip_roundtrip_150KB() {
        byte[] data = new byte[150 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) ((i * 3 + 17) % 256);
        assertRoundtrip(gzip, data);
    }

    // --- Algorithm name ---

    @Test
    public void testAlgorithmName() {
        assertEquals("DEFLATE", deflate.getAlgorithmName());
        assertEquals("GZIP", gzip.getAlgorithmName());
    }
}
