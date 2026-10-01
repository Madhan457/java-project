package com.securevault.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {
    private final EncryptionService encryptionService = new EncryptionService();

    @Test
    void decryptsWithTheSamePasswordAndRejectsTamperedCiphertext() throws Exception {
        byte[] plaintext = "SecureVault round-trip check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] salt = encryptionService.newSalt();
        byte[] iv = encryptionService.newIv();

        Cipher encryptor = encryptionService.encryptionCipher("correct horse battery", salt, iv);
        byte[] ciphertext = encryptor.doFinal(plaintext);
        Cipher decryptor = encryptionService.decryptionCipher("correct horse battery", salt, iv);
        assertArrayEquals(plaintext, decryptor.doFinal(ciphertext));

        ciphertext[ciphertext.length - 1] ^= 1;
        Cipher tamperedDecryptor = encryptionService.decryptionCipher("correct horse battery", salt, iv);
        assertThrows(AEADBadTagException.class, () -> tamperedDecryptor.doFinal(ciphertext));
    }

    @Test
    void createsIndependentSaltsAndTwelveByteIvs() {
        byte[] firstSalt = encryptionService.newSalt();
        byte[] secondSalt = encryptionService.newSalt();
        byte[] firstIv = encryptionService.newIv();
        byte[] secondIv = encryptionService.newIv();

        org.junit.jupiter.api.Assertions.assertEquals(16, firstSalt.length);
        org.junit.jupiter.api.Assertions.assertEquals(12, firstIv.length);
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(firstSalt, secondSalt));
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(firstIv, secondIv));
    }

    @Test
    void decryptsUsingTheIterationCountRecordedForTheFile() throws Exception {
        byte[] salt = encryptionService.newSalt();
        byte[] iv = encryptionService.newIv();
        byte[] plaintext = "per-file key derivation settings".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] ciphertext = encryptionService.encryptionCipher("file password", salt, iv, 1_000)
                .doFinal(plaintext);

        assertArrayEquals(plaintext,
                encryptionService.decryptionCipher("file password", salt, iv, 1_000).doFinal(ciphertext));
        assertThrows(AEADBadTagException.class,
                () -> encryptionService.decryptionCipher("file password", salt, iv, 1_001).doFinal(ciphertext));
    }
}