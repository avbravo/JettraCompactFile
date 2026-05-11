package io.jettra.jcf.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jettra.jcf.chunk.JettraChunker;
import io.jettra.jcf.core.JCFDecoder;
import io.jettra.jcf.security.JCFSecurity;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JCFFileHandler handles reading and writing of .jettracf files.
 */
public class JCFFileHandler {

    private static final String MAGIC_NUMBER = "JETTRACF";
    private static final String VERSION = "1.0";
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final long[] MULTIPLIERS = {10, 100, 1000, 1000000, 1000000000, 1000000000000L};
    private static final char[] MULTIPLIER_CHARS = {'D', 'C', 'M', 'Z', 'G', 'T'};

    public static void compressFile(File inputFile, File outputFile, String key) throws Exception {
        byte[] rawData;
        boolean isDir = inputFile.isDirectory();
        
        if (isDir) {
            Path tempZip = Files.createTempFile("jcf_dir_", ".zip");
            zipDirectory(inputFile, tempZip);
            rawData = Files.readAllBytes(tempZip);
            Files.delete(tempZip);
        } else {
            rawData = Files.readAllBytes(inputFile.toPath());
        }

        // --- BIT-LEVEL HYBRID ENGINE v4 ---
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(outStream);
        
        BitStream bitStream = new BitStream(rawData);
        while (bitStream.hasMore()) {
            int bit = bitStream.peek();
            long run = bitStream.countRun(bit);
            
            if (run > 32) { // Use scientific for long runs
                dos.writeByte(0xAA);
                dos.writeByte(bit);
                writeScientificRun(dos, run);
                bitStream.skip(run);
            } else {
                // Literal bits (pack into bytes)
                int literalBits = (int) Math.min(2040, bitStream.remaining()); // ~255 bytes
                dos.writeByte(0xBB);
                dos.writeByte(literalBits / 8 + (literalBits % 8 == 0 ? 0 : 1));
                
                byte[] packed = bitStream.readPacked(literalBits);
                dos.write(packed);
            }
        }
        dos.flush();

        byte[] finalData = outStream.toByteArray();
        byte[] compressed = compressBinary(finalData);
        byte[] encrypted = JCFSecurity.encrypt(compressed, key);

        try (DataOutputStream hDos = new DataOutputStream(new FileOutputStream(outputFile))) {
            hDos.write(MAGIC_NUMBER.getBytes(StandardCharsets.UTF_8));
            hDos.writeUTF(VERSION);
            hDos.writeBoolean(isDir);
            hDos.writeLong(rawData.length);
            hDos.writeUTF(inputFile.getName());
            hDos.writeInt(encrypted.length);
            hDos.write(encrypted);
        }
    }

    private static class BitStream {
        private final byte[] data;
        private long bitPos = 0;
        private final long totalBits;

        public BitStream(byte[] data) {
            this.data = data;
            this.totalBits = (long) data.length * 8;
        }

        public boolean hasMore() { return bitPos < totalBits; }
        public long remaining() { return totalBits - bitPos; }
        public int peek() { return (data[(int)(bitPos / 8)] >> (7 - (int)(bitPos % 8))) & 1; }
        public void skip(long n) { bitPos += n; }

        public long countRun(int bit) {
            long count = 0;
            while (bitPos + count < totalBits) {
                int b = (data[(int)((bitPos + count) / 8)] >> (7 - (int)((bitPos + count) % 8))) & 1;
                if (b == bit) count++;
                else break;
                if (count > 1000000000L) break;
            }
            return count;
        }

        public byte[] readPacked(int numBits) {
            byte[] out = new byte[(numBits + 7) / 8];
            for (int i = 0; i < numBits; i++) {
                if (!hasMore()) break;
                if (peek() == 1) {
                    out[i / 8] |= (1 << (7 - (i % 8)));
                }
                bitPos++;
            }
            return out;
        }
    }

    private static void writeScientificRun(DataOutputStream dos, long length) throws IOException {
        List<RunPart> parts = decomposeRun(length);
        dos.writeByte(parts.size());
        for (RunPart p : parts) {
            dos.writeByte(p.multiplierIndex);
            dos.writeByte(p.count);
        }
    }

    private record RunPart(int multiplierIndex, int count) {}

    private static List<RunPart> decomposeRun(long length) {
        List<RunPart> parts = new ArrayList<>();
        long remaining = length;
        for (int i = MULTIPLIERS.length - 1; i >= 0; i--) {
            while (remaining >= MULTIPLIERS[i]) {
                int safeCount = (int) Math.min(remaining / MULTIPLIERS[i], 255);
                parts.add(new RunPart(i, safeCount));
                remaining -= (long) safeCount * MULTIPLIERS[i];
            }
        }
        while (remaining > 0) {
            int safeCount = (int) Math.min(remaining, 255);
            parts.add(new RunPart(-1, safeCount));
            remaining -= safeCount;
        }
        return parts;
    }

    public static void decompressFile(File inputFile, File outputDir, String key) throws Exception {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(inputFile))) {
            byte[] magic = new byte[MAGIC_NUMBER.length()];
            dis.readFully(magic);
            if (!MAGIC_NUMBER.equals(new String(magic, StandardCharsets.UTF_8))) throw new IOException("Invalid format");
            
            dis.readUTF(); // version
            boolean isDir = dis.readBoolean();
            long originalSize = dis.readLong();
            String originalName = dis.readUTF();
            
            int dataLen = dis.readInt();
            byte[] encrypted = new byte[dataLen];
            dis.readFully(encrypted);
            
            byte[] compressed = JCFSecurity.decrypt(encrypted, key);
            byte[] jcfData = decompressBinary(compressed);
            
            BitReconstructor reconstructor = new BitReconstructor((int) originalSize);
            try (DataInputStream jcfDis = new DataInputStream(new ByteArrayInputStream(jcfData))) {
                while (jcfDis.available() > 0) {
                    int opcode = jcfDis.readByte() & 0xFF;
                    if (opcode == 0xAA) {
                        int bit = jcfDis.readByte();
                        int numParts = jcfDis.readByte();
                        long len = 0;
                        for (int i = 0; i < numParts; i++) {
                            int mIdx = jcfDis.readByte();
                            int count = jcfDis.readByte() & 0xFF;
                            len += (mIdx == -1) ? count : (long) count * MULTIPLIERS[mIdx];
                        }
                        reconstructor.addBits(bit, len);
                    } else if (opcode == 0xBB) {
                        int byteCount = jcfDis.readByte() & 0xFF;
                        byte[] lit = new byte[byteCount];
                        jcfDis.readFully(lit);
                        reconstructor.addPacked(lit);
                    }
                }
            }
            
            byte[] rawData = reconstructor.toByteArray();
            if (isDir) {
                Path tempZip = outputDir.toPath().resolve(originalName + "_temp.zip");
                Files.write(tempZip, rawData);
                unzip(tempZip, outputDir.toPath());
                Files.delete(tempZip);
            } else {
                Files.write(new File(outputDir, originalName).toPath(), rawData);
            }
        }
    }

    private static class BitReconstructor {
        private final byte[] data;
        private int currentBitPos = 7;
        private int totalBytesWritten = 0;

        public BitReconstructor(int size) { this.data = new byte[size]; }

        public void addBits(int bit, long length) {
            for (long i = 0; i < length; i++) {
                if (totalBytesWritten >= data.length) break;
                if (bit == 1) data[totalBytesWritten] |= (1 << currentBitPos);
                if (--currentBitPos < 0) { currentBitPos = 7; totalBytesWritten++; }
            }
        }

        public void addPacked(byte[] packed) {
            for (byte b : packed) {
                for (int i = 7; i >= 0; i--) {
                    addBits((b >> i) & 1, 1);
                }
            }
        }

        public byte[] toByteArray() { return data; }
    }

    private static byte[] compressBinary(byte[] data) throws IOException {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        return outputStream.toByteArray();
    }

    private static byte[] decompressBinary(byte[] data) throws Exception {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(data);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
        return outputStream.toByteArray();
    }

    private static void zipDirectory(File folder, Path zipFilePath) throws IOException {
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
            zos.setLevel(0);
            Path sourcePath = folder.toPath();
            Files.walk(sourcePath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                try {
                    String name = sourcePath.relativize(path).toString();
                    File file = path.toFile();
                    long size = file.length();
                    
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(name);
                    zipEntry.setMethod(java.util.zip.ZipEntry.STORED);
                    zipEntry.setSize(size);
                    
                    // Pre-calculate CRC
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            crc.update(buffer, 0, len);
                        }
                    }
                    zipEntry.setCrc(crc.getValue());
                    
                    zos.putNextEntry(zipEntry);
                    try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void unzip(Path zipFilePath, Path destPath) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = destPath.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    Files.copy(zis, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore or log
                    }
                });
        }
    }
}
