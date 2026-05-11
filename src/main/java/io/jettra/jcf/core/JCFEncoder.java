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

        StringBuilder sb = new StringBuilder(data.length / 2); // Initial estimate
        boolean currentBit = (data[0] & 0x80) != 0;
        long count = 0;

        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            
            // Optimization: If current byte is all same bits and matches currentBit
            if (currentBit && b == (byte) 0xFF) {
                count += 8;
                continue;
            } else if (!currentBit && b == (byte) 0x00) {
                count += 8;
                continue;
            }

            // Bit-by-bit for mixed bytes or transitions
            for (int j = 7; j >= 0; j--) {
                boolean bit = (b & (1 << j)) != 0;
                if (bit == currentBit) {
                    count++;
                } else {
                    if (count > 0) sb.append(formatRun(count, currentBit));
                    currentBit = bit;
                    count = 1;
                }
            }
        }
        if (count > 0) sb.append(formatRun(count, currentBit));

        return sb.toString();
    }

    private static String formatRun(long count, boolean bit) {
        char symbol = bit ? 'L' : '*';
        if (count == 1) return String.valueOf(symbol);
        return formatCount(count) + symbol;
    }

    private static String formatCount(long count) {
        if (count == 0) return "";
        
        StringBuilder sb = new StringBuilder();
        long remaining = count;

        // Use multipliers from largest to smallest
        if (remaining >= 1_000_000_000_000L) {
            sb.append(remaining / 1_000_000_000_000L).append('T');
            remaining %= 1_000_000_000_000L;
        }
        if (remaining >= 1_000_000_000L) {
            sb.append(remaining / 1_000_000_000L).append('G');
            remaining %= 1_000_000_000L;
        }
        if (remaining >= 1_000_000L) {
            sb.append(remaining / 1_000_000L).append('Z');
            remaining %= 1_000_000L;
        }
        if (remaining >= 1_000L) {
            sb.append(remaining / 1_000L).append('M');
            remaining %= 1_000L;
        }
        if (remaining >= 100L) {
            sb.append(remaining / 100L).append('C');
            remaining %= 100L;
        }
        if (remaining >= 10L) {
            sb.append(remaining / 10L).append('D');
            remaining %= 10L;
        }
        if (remaining > 0) {
            sb.append(remaining);
        }
        
        return sb.toString();
    }
}
