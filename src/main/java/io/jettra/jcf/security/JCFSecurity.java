package io.jettra.jcf.security;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * JCFSecurity handles encryption and decryption of chunks using AES.
 */
public class JCFSecurity {

    private static final String ALGORITHM = "AES";

    public static byte[] encrypt(byte[] data, String key) throws Exception {
        SecretKeySpec secretKey = deriveKey(key);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] data, String key) throws Exception {
        SecretKeySpec secretKey = deriveKey(key);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(data);
    }

    private static SecretKeySpec deriveKey(String key) throws Exception {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        keyBytes = sha.digest(keyBytes);
        keyBytes = Arrays.copyOf(keyBytes, 16); // Use 128 bit for simplicity/compatibility
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
