package io.jettra.jcf.core;

import java.util.ArrayList;
import java.util.List;

/**
 * JCFEncoder implements the custom compression scheme for JettraCompactFile.
 * It converts bit sequences into a compact string representation using:
 * - '*' for 0 bits
 * - 'L' for 1 bits
 * - Multipliers: D(10), C(100), M(1000), Z(10^6), G(10^9), T(10^12)
 */
public class JCFEncoder {

    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean currentBit = (data[0] & 0x80) != 0;
        long count = 0;

        for (byte b : data) {
            for (int i = 7; i >= 0; i--) {
                boolean bit = (b & (1 << i)) != 0;
                if (bit == currentBit) {
                    count++;
                } else {
                    sb.append(formatRun(count, currentBit));
                    currentBit = bit;
                    count = 1;
                }
            }
        }
        sb.append(formatRun(count, currentBit));

        return sb.toString();
    }

    private static String formatRun(long count, boolean bit) {
        char symbol = bit ? 'L' : '*';
        return formatCount(count) + symbol;
    }

    private static String formatCount(long count) {
        if (count == 0) return "";
        
        // Strategy: Use the largest multipliers for trailing zeros
        if (count % 1_000_000_000_000L == 0) return (count / 1_000_000_000_000L) + "T";
        if (count % 1_000_000_000L == 0) return (count / 1_000_000_000L) + "G";
        if (count % 1_000_000L == 0) return (count / 1_000_000L) + "Z";
        if (count % 1_000L == 0) return (count / 1_000L) + "M";
        if (count % 100L == 0) return (count / 100L) + "C";
        if (count % 10L == 0) return (count / 10L) + "D";
        
        return String.valueOf(count);
    }
}
