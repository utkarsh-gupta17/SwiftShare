package p2p.utils;

import p2p.compression.CompressionStrategy;
import p2p.compression.CompressionFactory;
import java.util.zip.GZIPInputStream;

public class CompressionUtils {

    public static byte[] compress(byte[] data, String filename) {
        if (data == null || data.length == 0) return data;

        // Get optimal compression strategy
        CompressionStrategy strategy = CompressionFactory.getOptimalStrategy(data, filename);

        System.out.printf("Compressing %s using %s algorithm (size: %d bytes)\n",
                         filename, strategy.getAlgorithmName(), data.length);

        return strategy.compress(data, filename);
    }

    public static byte[] decompress(byte[] data, String filename) {
        if (data == null) return new byte[0];
        if (data.length == 0) return data;

        String algorithm = detectAlgorithm(data);
        CompressionStrategy strategy = "GZIP".equals(algorithm)
            ? CompressionFactory.getStrategy("GZIP")
            : CompressionFactory.getStrategy("DEFLATE");
        return strategy.decompress(data, filename);
    }

    public static String detectAlgorithm(byte[] data) {
        if (data == null || data.length < 2) {
            return "none";
        }

        // Simple heuristic: check for GZIP magic numbers
        if (data[0] == (byte) GZIPInputStream.GZIP_MAGIC_BYTE1 &&
            data[1] == (byte) GZIPInputStream.GZIP_MAGIC_BYTE2) {
            return "GZIP";
        }

        // Default to DEFLATE for everything else
        return "DEFLATE";
    }
}