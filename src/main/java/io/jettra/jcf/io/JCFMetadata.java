package io.jettra.jcf.io;

import java.util.List;

/**
 * Metadata structure for JettraCompactFile.
 */
public record JCFMetadata(
    String version,
    String originalName,
    boolean isDirectory,
    long originalSize,
    List<Integer> chunkSequence, // Indices into uniqueChunks
    List<ChunkInfo> uniqueChunks
) {
    public record ChunkInfo(
        int id,
        long offset,
        long compressedSize,
        long originalSize
    ) {}
}
