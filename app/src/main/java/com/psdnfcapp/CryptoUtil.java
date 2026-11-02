package com.psdnfcapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encryption/decryption using a shared secret key (passphrase).
 * The same passphrase must be entered on the sender and the intended receiver.
 */
public final class CryptoUtil {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private CryptoUtil() {
    }

    public static javax.crypto.SecretKey deriveKey(String passphrase) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = md.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext and returns Base64(IV || ciphertext).
     */
    public static String encrypt(String plaintext, javax.crypto.SecretKey key) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, combined, 0, IV_LEN);
        System.arraycopy(ct, 0, combined, IV_LEN, ct.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts Base64(IV || ciphertext) back to plaintext.
     */
    public static String decrypt(String ciphertextBase64, javax.crypto.SecretKey key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
        if (combined.length < IV_LEN + 1) {
            throw new Exception("Ciphertext too short");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(combined, IV_LEN, combined.length);
        Cipher c = Cipher.getInstance(TRANSFORMATION);
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }
}
