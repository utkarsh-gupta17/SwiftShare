package p2p.compression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CompressionFactoryTest {

    @Test
    public void testGetStrategyDeflate() {
        CompressionStrategy s = CompressionFactory.getStrategy("DEFLATE");
        assertNotNull(s);
        assertEquals("DEFLATE", s.getAlgorithmName());
    }

    @Test
    public void testGetStrategyGzip() {
        CompressionStrategy s = CompressionFactory.getStrategy("GZIP");
        assertNotNull(s);
        assertEquals("GZIP", s.getAlgorithmName());
    }

    @Test
    public void testGetStrategyUnknown_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> CompressionFactory.getStrategy("BZIP2"));
    }

    // --- Priority: GZIP first (≥100KB), DEFLATE for smaller files ---

    @Test
    public void testPriorityOverlapZone_150KB_usesGzip() {
        // 150KB ≥ 100KB → GZIP wins (checked first in PR)
        byte[] data = new byte[150 * 1024];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "file.bin");
        assertEquals("GZIP", s.getAlgorithmName());
    }

    @Test
    public void testPriorityLargeFile_2MB_usesGzip() {
        byte[] data = new byte[2 * 1024 * 1024];
        CompressionStrategy s = CompressionFactory.getOptimalStrategy(data, "large.bin");
        assertEquals("GZIP", s.getAlgorithmName());
    }

    @Test
    public void testPrioritySmallFile_500B_usesDeflate() {
        // 500B < 100KB → GZIP not suitable, DEFLATE not suitable, returns DEFLATE default
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
