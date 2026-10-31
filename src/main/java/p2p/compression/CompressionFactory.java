package p2p.compression;


import java.util.HashMap;
import java.util.Map;

public class CompressionFactory {

    private static final Map<String, CompressionStrategy> strategies = new HashMap<>();

    static {
        strategies.put("DEFLATE", new DeflateCompressionStrategy());
        strategies.put("GZIP", new GzipCompressionStrategy());
    }

    public static CompressionStrategy getOptimalStrategy(byte[] data, String filename) {
        // Check GZIP first
        if (strategies.get("GZIP").isSuitable(data, filename)) {
            return strategies.get("GZIP");
        }

        // If GZIP algorithm is not suitable, return DEFLATE as default
        return strategies.get("DEFLATE");
    }

    public static CompressionStrategy getStrategy(String algorithmName) {
        CompressionStrategy s = strategies.get(algorithmName);
        if (s == null) {
            throw new IllegalArgumentException("Unknown compression algorithm: " + algorithmName);
        }
        return s;
    }
}
