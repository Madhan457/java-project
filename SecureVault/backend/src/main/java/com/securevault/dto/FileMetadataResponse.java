package com.securevault.dto;

import java.time.Instant;

public record FileMetadataResponse(
        Long id,
        String originalFilename,
        long fileSize,
        String encryptionAlgorithm,
        Instant uploadedAt) {
}