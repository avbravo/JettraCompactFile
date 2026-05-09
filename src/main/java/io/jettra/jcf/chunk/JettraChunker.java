package io.jettra.jcf.chunk;

import io.jettra.jcf.core.JCFEncoder;
import io.jettra.jcf.security.JCFSecurity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JettraChunker manages the division of files into chunks and their parallel processing
 * using Java Virtual Threads.
 */
public class JettraChunker {

    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    public record ProcessedChunk(int index, byte[] data, long originalSize) {}

    public static List<ProcessedChunk> processChunks(byte[] fileData, String key) throws Exception {
        int totalChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);
        List<ProcessedChunk> processedChunks = Collections.synchronizedList(new ArrayList<>());
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < totalChunks; i++) {
                final int index = i;
                int start = i * CHUNK_SIZE;
                int end = Math.min(fileData.length, start + CHUNK_SIZE);
                byte[] chunk = new byte[end - start];
                System.arraycopy(fileData, start, chunk, 0, chunk.length);

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        // 1. Encode bit repetition
                        String encoded = JCFEncoder.encode(chunk);
                        byte[] encodedBytes = encoded.getBytes(StandardCharsets.UTF_8);

                        // 2. Encrypt
                        byte[] encrypted = JCFSecurity.encrypt(encodedBytes, key);

                        processedChunks.add(new ProcessedChunk(index, encrypted, chunk.length));
                    } catch (Exception e) {
                        throw new RuntimeException("Error processing chunk " + index, e);
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // Sort chunks by index to ensure correct order
        processedChunks.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return processedChunks;
    }
}
