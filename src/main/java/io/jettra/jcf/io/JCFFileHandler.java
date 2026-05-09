package io.jettra.jcf.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jettra.jcf.chunk.JettraChunker;
import io.jettra.jcf.core.JCFDecoder;
import io.jettra.jcf.security.JCFSecurity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    public static void compressFile(File inputFile, File outputFile, String key) throws Exception {
        byte[] fileData = Files.readAllBytes(inputFile.toPath());
        List<JettraChunker.ProcessedChunk> chunks = JettraChunker.processChunks(fileData, key);

        List<JCFMetadata.ChunkInfo> chunkInfos = new ArrayList<>();
        long currentOffset = 0;
        
        ByteArrayOutputStream chunkStream = new ByteArrayOutputStream();
        for (JettraChunker.ProcessedChunk chunk : chunks) {
            chunkInfos.add(new JCFMetadata.ChunkInfo(chunk.index(), currentOffset, chunk.data().length, chunk.originalSize()));
            chunkStream.write(chunk.data());
            currentOffset += chunk.data().length;
        }

        JCFMetadata metadata = new JCFMetadata(VERSION, inputFile.getName(), false, fileData.length, chunkInfos);
        byte[] metadataBytes = mapper.writeValueAsBytes(metadata);

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(outputFile))) {
            dos.writeUTF(MAGIC_NUMBER);
            dos.writeUTF(VERSION);
            dos.writeInt(metadataBytes.length);
            dos.write(metadataBytes);
            dos.write(chunkStream.toByteArray());
        }
    }

    public static void decompressFile(File inputFile, File outputDir, String key) throws Exception {
        Path tempDir = outputDir.toPath().resolve(".jettra-jcf-uncomprimed-temp");
        Files.createDirectories(tempDir);

        try (DataInputStream dis = new DataInputStream(new FileInputStream(inputFile))) {
            String magic = dis.readUTF();
            if (!MAGIC_NUMBER.equals(magic)) throw new IOException("Invalid file format");
            String version = dis.readUTF();
            int metadataLen = dis.readInt();
            byte[] metadataBytes = new byte[metadataLen];
            dis.readFully(metadataBytes);
            JCFMetadata metadata = mapper.readValue(metadataBytes, JCFMetadata.class);

            byte[] allChunkData = dis.readAllBytes();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (JCFMetadata.ChunkInfo info : metadata.chunks()) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            byte[] encryptedData = new byte[(int) info.compressedSize()];
                            System.arraycopy(allChunkData, (int) info.offset(), encryptedData, 0, encryptedData.length);

                            // 1. Decrypt
                            byte[] decryptedBytes = JCFSecurity.decrypt(encryptedData, key);
                            String encoded = new String(decryptedBytes, StandardCharsets.UTF_8);

                            // 2. Decode
                            byte[] originalData = JCFDecoder.decode(encoded);

                            // 3. Write to temp
                            Path chunkPath = tempDir.resolve("chunk_" + info.index());
                            Files.write(chunkPath, originalData);
                        } catch (Exception e) {
                            throw new RuntimeException("Error decompressing chunk " + info.index(), e);
                        }
                    }, executor));
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            // Reconstruct final file
            File finalFile = new File(outputDir, metadata.originalName());
            try (FileOutputStream fos = new FileOutputStream(finalFile)) {
                for (int i = 0; i < metadata.chunks().size(); i++) {
                    Path chunkPath = tempDir.resolve("chunk_" + i);
                    Files.copy(chunkPath, fos);
                    Files.delete(chunkPath);
                }
            }
            Files.delete(tempDir);
        }
    }
}
