package com.securevault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "file_metadata")
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "encrypted_filename", nullable = false, unique = true, length = 255)
    private String encryptedFilename;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "encryption_algorithm", nullable = false, length = 40)
    private String encryptionAlgorithm;

    @Column(name = "pbkdf2_iterations", nullable = false)
    private int pbkdf2Iterations;

    @Lob
    @Column(nullable = false)
    private byte[] salt;

    @Lob
    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    protected FileMetadata() {
    }

    public FileMetadata(User user, String originalFilename, String encryptedFilename, long fileSize,
                        String encryptionAlgorithm, int pbkdf2Iterations, byte[] salt, byte[] iv) {
        this.user = user;
        this.originalFilename = originalFilename;
        this.encryptedFilename = encryptedFilename;
        this.fileSize = fileSize;
        this.encryptionAlgorithm = encryptionAlgorithm;
        this.pbkdf2Iterations = pbkdf2Iterations;
        this.salt = salt.clone();
        this.iv = iv.clone();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getOriginalFilename() { return originalFilename; }
    public String getEncryptedFilename() { return encryptedFilename; }
    public long getFileSize() { return fileSize; }
    public String getEncryptionAlgorithm() { return encryptionAlgorithm; }
    public int getPbkdf2Iterations() { return pbkdf2Iterations; }
    public byte[] getSalt() { return salt.clone(); }
    public byte[] getIv() { return iv.clone(); }
    public Instant getUploadedAt() { return uploadedAt; }
}