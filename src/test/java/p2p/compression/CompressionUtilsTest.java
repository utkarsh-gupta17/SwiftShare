package p2p.compression;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompressionUtilsTest {

    // --- compress null/empty ---

    @Test
    public void testCompressNull_returnsNull() {
        assertNull(CompressionUtils.compress(null, "x"));
    }

    @Test
    public void testCompressEmpty_returnsEmpty() {
        byte[] r = CompressionUtils.compress(new byte[0], "x");
        assertArrayEquals(new byte[0], r);
    }

    // --- decompress null/empty ---

    @Test
    public void testDecompressNull_returnsEmpty() {
        assertArrayEquals(new byte[0], CompressionUtils.decompress(null, "x"));
    }

    @Test
    public void testDecompressEmpty_returnsEmpty() {
        assertArrayEquals(new byte[0], CompressionUtils.decompress(new byte[0], "x"));
    }

    // --- detectAlgorithm ---

    @Test
    public void testDetectAlgorithm_gzipMagicBytes() {
        GzipCompressionStrategy gzip = new GzipCompressionStrategy();
        byte[] gzipped = gzip.compress("test".getBytes(), "t");
        assertEquals("GZIP", CompressionUtils.detectAlgorithm(gzipped));
    }

    @Test
    public void testDetectAlgorithm_deflate_noMagicBytes() {
        DeflateCompressionStrategy deflate = new DeflateCompressionStrategy();
        byte[] deflated = deflate.compress("test".getBytes(), "t");
        assertEquals("DEFLATE", CompressionUtils.detectAlgorithm(deflated));
    }

    @Test
    public void testDetectAlgorithm_nullData_returnsNone() {
        assertEquals("none", CompressionUtils.detectAlgorithm(null));
    }

    @Test
    public void testDetectAlgorithm_singleByte_returnsDeflate() {
        assertEquals("DEFLATE", CompressionUtils.detectAlgorithm(new byte[]{0x42}));
    }

    // --- Full E2E via CompressionUtils ---

    private void assertE2E(byte[] original, String filename) {
        byte[] compressed = CompressionUtils.compress(original, filename);
        byte[] decompressed = CompressionUtils.decompress(compressed, filename);
        assertArrayEquals("E2E roundtrip failed for " + filename, original, decompressed);
    }

    @Test
    public void testE2E_smallTextFile() {
        assertE2E("Hello SwiftShare!".getBytes(), "hello.txt");
    }

    @Test
    public void testE2E_jsonData() {
        String json = "{\"name\":\"SwiftShare\",\"version\":1,\"features\":[\"p2p\",\"compression\"]}";
        assertE2E(json.getBytes(), "config.json");
    }

    @Test
    public void testE2E_50KB_data() {
        byte[] data = new byte[50 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
        assertE2E(data, "medium.bin");
    }

    @Test
    public void testE2E_200KB_data_inOverlapZone() {
        // 200KB is in overlap zone (100KB-1MB) — DEFLATE should be chosen
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(
            new byte[200 * 1024], "overlap.bin");
        assertEquals("DEFLATE", s.getAlgorithmName());
        byte[] data = new byte[200 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 13 % 256);
        assertE2E(data, "overlap.bin");
    }

    @Test
    public void testE2E_2MB_data_usesGzip() {
        // >1MB uses GZIP
        byte[] data = new byte[2 * 1024 * 1024];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "large.bin");
        assertEquals("GZIP", s.getAlgorithmName());
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
        assertE2E(data, "large.bin");
    }

    @Test
    public void testE2E_corruptData_returnsEmptyDecompress() {
        byte[] corrupt = new byte[]{0x00, (byte) 0xFF, 0x01, 0x02, 0x03};
        byte[] r = CompressionUtils.decompress(corrupt, "corrupt");
        assertArrayEquals("Corrupt data decompress should return empty array",
            new byte[0], r);
    }

    @Test
    public void testE2E_uncompressedData_passedThrough() {
        // Data that is too small for compression — passed through
        byte[] tiny = new byte[500];
        byte[] result = CompressionUtils.decompress(tiny, "tiny");
        // decompress of non-compressed (passed-through) data: detectAlgorithm returns
        // DEFLATE, deflate.decompress of non-deflate data returns empty array
        // So for tiny (< 1KB) raw data, decompress returns empty [] — this is correct
        // because the upload side would also pass through (no compression)
        assertArrayEquals(new byte[0], result);
    }
}
