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
    List<ChunkInfo> chunks
) {
    public record ChunkInfo(
        int index,
        long offset,
        long compressedSize,
        long originalSize
    ) {}
}
