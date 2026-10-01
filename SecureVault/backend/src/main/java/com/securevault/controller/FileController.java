package com.securevault.controller;

import com.securevault.dto.FileMetadataResponse;
import com.securevault.entity.FileMetadata;
import com.securevault.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/encrypt")
    public FileMetadataResponse encrypt(java.security.Principal principal,
            @RequestParam("file") MultipartFile file, @RequestParam("password") String password) throws Exception {
        return fileService.encrypt(principal.getName(), file, password);
    }

    @GetMapping
    public List<FileMetadataResponse> list(java.security.Principal principal) {
        return fileService.list(principal.getName());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(java.security.Principal principal, @PathVariable Long id) {
        FileMetadata metadata = fileService.ownedFile(principal.getName(), id);
        Path path = fileService.encryptedPath(metadata);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileSize(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(metadata.getEncryptedFilename()).build().toString())
                .body(new FileSystemResource(path));
    }

    @PostMapping("/{id}/decrypt")
    public ResponseEntity<StreamingResponseBody> decrypt(java.security.Principal principal, @PathVariable Long id,
            @RequestParam("password") String password) throws IOException {
        FileMetadata metadata = fileService.ownedFile(principal.getName(), id);
        Path temporaryFile = fileService.decryptToTemporaryFile(metadata, password);
        StreamingResponseBody body = output -> {
            try (InputStream input = Files.newInputStream(temporaryFile)) {
                input.transferTo(output);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(fileSize(temporaryFile))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(metadata.getOriginalFilename()).build().toString())
                .body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(java.security.Principal principal, @PathVariable Long id) throws IOException {
        fileService.delete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read stored file");
        }
    }
}