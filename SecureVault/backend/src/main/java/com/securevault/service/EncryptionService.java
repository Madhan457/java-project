package com.securevault.service;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EncryptionService {
    public static final String ALGORITHM = "AES-256-GCM";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION = "PBKDF2WithHmacSHA256";
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${securevault.encryption.pbkdf2-iterations:600000}")
    private int iterations = 600_000;

    public int iterations() {
        if (iterations < 1) {
            throw new IllegalStateException("PBKDF2 iterations must be greater than zero");
        }
        return iterations;
    }

    public byte[] newSalt() {
        return randomBytes(SALT_BYTES);
    }

    public byte[] newIv() {
        return randomBytes(IV_BYTES);
    }

    public Cipher encryptionCipher(String password, byte[] salt, byte[] iv) throws GeneralSecurityException {
        return encryptionCipher(password, salt, iv, iterations());
    }

    public Cipher encryptionCipher(String password, byte[] salt, byte[] iv, int iterations)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), new GCMParameterSpec(TAG_BITS, iv));
        return cipher;
    }

    public Cipher decryptionCipher(String password, byte[] salt, byte[] iv) throws GeneralSecurityException {
        return decryptionCipher(password, salt, iv, iterations());
    }

    public Cipher decryptionCipher(String password, byte[] salt, byte[] iv, int iterations)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), new GCMParameterSpec(TAG_BITS, iv));
        return cipher;
    }

    private SecretKeySpec deriveKey(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        if (iterations < 1) {
            throw new IllegalArgumentException("PBKDF2 iterations must be greater than zero");
        }
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance(KEY_DERIVATION).generateSecret(keySpec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            keySpec.clearPassword();
        }
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }
}