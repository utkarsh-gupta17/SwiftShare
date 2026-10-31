package p2p.compression;
import p2p.utils.CompressionUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public class E2ECompressionTest {

    @Test
    public void testCompressThenDecompressRoundtrip() throws Exception {
        String original = "Hello SwiftShare! This is a test of the compression roundtrip.";
        byte[] data = original.getBytes("UTF-8");
        byte[] compressed = CompressionUtils.compress(data, "test.txt");
        byte[] decompressed = CompressionUtils.decompress(compressed, "test.txt");
        assertArrayEquals(data, decompressed);
    }

    @Test
    public void testCompressAndDecompressGzipRange() throws Exception {
        // File > 100KB triggers GZIP
        byte[] largeData = new byte[150 * 1024];
        for (int i = 0; i < largeData.length; i++) largeData[i] = (byte) (i % 256);
        byte[] compressed = CompressionUtils.compress(largeData, "large.bin");
        byte[] decompressed = CompressionUtils.decompress(compressed, "large.bin");
        assertArrayEquals(largeData, decompressed);
    }

    @Test
    public void testCompressAndDecompressDeflateRange() throws Exception {
        // 1KB-1MB triggers DEFLATE
        byte[] mediumData = new byte[50 * 1024];
        for (int i = 0; i < mediumData.length; i++) mediumData[i] = (byte) ((i * 3) % 256);
        byte[] compressed = CompressionUtils.compress(mediumData, "medium.bin");
        byte[] decompressed = CompressionUtils.decompress(compressed, "medium.bin");
        assertArrayEquals(mediumData, decompressed);
    }

    @Test
    public void testNullAndEmptyData() {
        assertNull(CompressionUtils.compress(null, "x"));
        assertArrayEquals(new byte[0], CompressionUtils.compress(new byte[0], "x"));
    }
}
