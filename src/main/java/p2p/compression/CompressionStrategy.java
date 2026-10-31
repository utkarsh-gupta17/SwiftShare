package p2p.compression;

public interface CompressionStrategy {
    byte[] compress(byte[] data, String filename);
    byte[] decompress(byte[] data, String filename);
    String getAlgorithmName();
    boolean isSuitable(byte[] data, String filename);
}
