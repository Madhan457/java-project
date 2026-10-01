package com.securevault.repository;

import com.securevault.entity.FileMetadata;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findAllByUserIdOrderByUploadedAtDesc(Long userId);
    Optional<FileMetadata> findByIdAndUserId(Long id, Long userId);
}