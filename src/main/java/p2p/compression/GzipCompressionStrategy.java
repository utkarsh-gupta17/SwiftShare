package p2p.compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressionStrategy implements CompressionStrategy {

    private static final int BUFFER_SIZE = 8192;

    @Override
    public byte[] compress(byte[] data, String filename) {
        if (data == null || data.length == 0) return data;

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {

            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("GZIP compression failed for " + filename);
            return data;
        }
    }

    @Override
    public byte[] decompress(byte[] data, String filename) {
        if (data == null) return new byte[0];
        if (data.length == 0) return data;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzipIn = new GZIPInputStream(bais)) {

            return gzipIn.readAllBytes();
        } catch (Exception e) {
            System.err.println("GZIP decompression failed for " + filename);
            return new byte[0];
        }
    }

    @Override
    public String getAlgorithmName() {
        return "GZIP";
    }

    @Override
    public boolean isSuitable(byte[] data, String filename) {
        if (data == null || data.length == 0) return false;

        int sizeInKB = data.length / 1024;
        return sizeInKB >= 100;
    }
}