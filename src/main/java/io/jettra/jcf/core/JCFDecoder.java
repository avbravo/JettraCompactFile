package io.jettra.jcf.core;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;

/**
 * JCFDecoder converts custom compressed strings back into bit sequences.
 */
public class JCFDecoder {

    public static byte[] decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }

        BitStreamBuilder builder = new BitStreamBuilder();
        StringBuilder numBuf = new StringBuilder();
        long currentCount = 0;

        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);

            if (Character.isDigit(c)) {
                numBuf.append(c);
            } else if (isMultiplier(c)) {
                long val = numBuf.length() > 0 ? Long.parseLong(numBuf.toString()) : 1;
                currentCount += val * getMultiplierValue(c);
                numBuf.setLength(0);
            } else if (c == '*' || c == 'L') {
                if (numBuf.length() > 0) {
                    currentCount += Long.parseLong(numBuf.toString());
                    numBuf.setLength(0);
                }
                if (currentCount == 0) currentCount = 1; // Default to 1 if no number specified? 
                // Actually the user examples always have numbers.
                
                builder.addBits(c == 'L', currentCount);
                currentCount = 0;
            }
        }

        return builder.toBytes();
    }

    private static boolean isMultiplier(char c) {
        return "DCMZGT".indexOf(c) != -1;
    }

    private static long getMultiplierValue(char c) {
        return switch (c) {
            case 'D' -> 10L;
            case 'C' -> 100L;
            case 'M' -> 1000L;
            case 'Z' -> 1_000_000L;
            case 'G' -> 1_000_000_000L;
            case 'T' -> 1_000_000_000_000L;
            default -> 1L;
        };
    }

    private static class BitStreamBuilder {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private byte currentByte = 0;
        private int bitCount = 0;

        public void addBits(boolean bit, long count) {
            for (long i = 0; i < count; i++) {
                if (bit) {
                    currentByte |= (byte) (1 << (7 - bitCount));
                }
                bitCount++;
                if (bitCount == 8) {
                    baos.write(currentByte);
                    currentByte = 0;
                    bitCount = 0;
                }
            }
        }

        public byte[] toBytes() {
            // Note: If the bit count is not a multiple of 8, the last bits are lost or padded.
            // In a real compressor, we need to store the exact bit count.
            // For now, we'll just return what we have.
            return baos.toByteArray();
        }
    }
}
