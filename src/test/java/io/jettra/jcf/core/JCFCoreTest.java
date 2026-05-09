package io.jettra.jcf.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JCFCoreTest {

    @Test
    public void testEncodeDecode() {
        byte[] original = new byte[]{ (byte) 0b11110000, (byte) 0b10101010 };
        // 1111 0000 1010 1010
        // Runs: 4 ones, 4 zeros, 1 one, 1 zero, 1 one, 1 zero, 1 one, 1 zero, 1 one, 1 zero
        // Expected: 4L4*1L1*1L1*1L1*1L1*
        
        String encoded = JCFEncoder.encode(original);
        System.out.println("Encoded: " + encoded);
        
        byte[] decoded = JCFDecoder.decode(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    public void testMultipliers() {
        // Test with 20 zeros (2D*)
        byte[] twentyZeros = new byte[3]; // 24 bits
        // Let's just test the formatting/parsing
        
        String encoded = JCFEncoder.encode(new byte[100]); // 800 bits -> 8C*
        assertTrue(encoded.contains("8C*"));
        
        byte[] decoded = JCFDecoder.decode(encoded);
        assertEquals(100, decoded.length);
        for(byte b : decoded) assertEquals(0, b);
    }
    
    @Test
    public void testComplexPattern() {
        String custom = "8*3L8*6L13*1DL7*9L3*";
        byte[] decoded = JCFDecoder.decode(custom);
        String reEncoded = JCFEncoder.encode(decoded);
        // Note: re-encoding might use different multipliers if the logic prefers shorter strings
        // but it should represent the same bitstream.
        
        byte[] reDecoded = JCFDecoder.decode(reEncoded);
        assertArrayEquals(decoded, reDecoded);
    }
}
