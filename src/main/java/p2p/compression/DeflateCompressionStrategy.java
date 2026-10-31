package p2p.compression;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class DeflateCompressionStrategy implements CompressionStrategy {

    private static final int BUFFER_SIZE = 8192;

    @Override
    public byte[] compress(byte[] data, String filename) {
        if (data == null || data.length == 0) return data;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Deflater deflater = new Deflater();

        try {
            deflater.setInput(data);
            deflater.finish();

            byte[] buffer = new byte[BUFFER_SIZE];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            return outputStream.toByteArray();
        } catch (Exception e) {
            System.err.println("DEFLATE compression failed for " + filename);
            return data;
        } finally {
            deflater.end();
        }
    }

    @Override
    public byte[] decompress(byte[] data, String filename) {
        if (data == null) return new byte[0];
        if (data.length == 0) return data;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Inflater inflater = new Inflater();

        try {
            inflater.setInput(data);

            byte[] buffer = new byte[BUFFER_SIZE];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            return outputStream.toByteArray();
        } catch (Exception e) {
            System.err.println("DEFLATE decompression failed for " + filename);
            return new byte[0];
        } finally {
            inflater.end();
        }
    }

    @Override
    public String getAlgorithmName() {
        return "DEFLATE";
    }

    @Override
    public boolean isSuitable(byte[] data, String filename) {
        if (data == null || data.length == 0) return false;

        int sizeInKB = data.length / 1024;
        return sizeInKB >= 1 && sizeInKB <= 1024;
    }
}