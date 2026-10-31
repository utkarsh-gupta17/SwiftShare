package p2p.compression;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompressionFactoryTest {

    @Test
    public void testGetStrategyDeflate() {
        assertNotNull(CompressionFactory.getStrategy("DEFLATE"));
        assertEquals("DEFLATE", CompressionFactory.getStrategy("DEFLATE").getAlgorithmName());
    }

    @Test
    public void testGetStrategyGzip() {
        assertNotNull(CompressionFactory.getStrategy("GZIP"));
        assertEquals("GZIP", CompressionFactory.getStrategy("GZIP").getAlgorithmName());
    }

    @Test
    public void testGetStrategyUnknown() {
        assertNull(CompressionFactory.getStrategy("BZIP2"));
    }

    // --- Priority: DEFLATE wins overlap zone (100KB-1MB) ---

    @Test
    public void testPriorityOverlapZone_150KB_usesDeflate() {
        byte[] data = new byte[150 * 1024];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "file.bin");
        assertEquals("DEFLATE", s.getAlgorithmName());
    }

    @Test
    public void testPriorityLargeFile_2MB_usesGzip() {
        byte[] data = new byte[2 * 1024 * 1024];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "large.bin");
        assertEquals("GZIP", s.getAlgorithmName());
    }

    @Test
    public void testPrioritySmallFile_500B_usesDeflate() {
        byte[] data = new byte[500];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "tiny.txt");
        assertEquals("DEFLATE", s.getAlgorithmName());
    }

    @Test
    public void testPriorityVerySmallFile_50B_usesDeflate() {
        byte[] data = new byte[50];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "tiny.txt");
        assertEquals("DEFLATE", s.getAlgorithmName());
    }

    @Test
    public void testPriorityNullData_returnsDeflate() {
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(null, "x");
        assertEquals("DEFLATE", s.getAlgorithmName());
    }
}
