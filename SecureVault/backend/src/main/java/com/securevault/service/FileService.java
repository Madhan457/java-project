package com.securevault.service;

import com.securevault.dto.FileMetadataResponse;
import com.securevault.entity.FileMetadata;
import com.securevault.entity.User;
import com.securevault.repository.FileMetadataRepository;
import com.securevault.util.FileUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.List;
import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileService {
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;
    private final FileMetadataRepository fileRepository;
    private final AuthService authService;
    private final EncryptionService encryptionService;
    private final Path storageDirectory;

    public FileService(FileMetadataRepository fileRepository, AuthService authService,
                       EncryptionService encryptionService,
                       @Value("${securevault.storage-dir:../storage/encrypted}") String storageDirectory) {
        this.fileRepository = fileRepository;
        this.authService = authService;
        this.encryptionService = encryptionService;
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initializeStorageDirectory() throws IOException {
        Files.createDirectories(storageDirectory);
    }

    @Transactional
    public FileMetadataResponse encrypt(String email, MultipartFile upload, String password)
            throws IOException, GeneralSecurityException {
        if (upload == null || upload.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty file");
        }
        if (upload.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Files must be 100 MB or smaller");
        }
        if (password == null || password.length() < 10 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encryption password must be 10 to 72 UTF-8 bytes");
        }

        User user = authService.findByEmail(email);
        String originalFilename = FileUtil.safeOriginalFilename(upload.getOriginalFilename());
        String encryptedFilename = FileUtil.encryptedFilename();
        byte[] salt = encryptionService.newSalt();
        byte[] iv = encryptionService.newIv();
        int pbkdf2Iterations = encryptionService.iterations();
        Path target = FileUtil.safeResolve(storageDirectory, encryptedFilename);
        Path partial = FileUtil.safeResolve(storageDirectory, encryptedFilename + ".part");
        Files.createDirectories(storageDirectory);

        try {
            Cipher cipher = encryptionService.encryptionCipher(password, salt, iv, pbkdf2Iterations);
            try (InputStream input = upload.getInputStream(); OutputStream output = Files.newOutputStream(partial);
                 javax.crypto.CipherOutputStream encrypted = new javax.crypto.CipherOutputStream(output, cipher)) {
                input.transferTo(encrypted);
            }
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            FileMetadata metadata = fileRepository.save(new FileMetadata(user, originalFilename, encryptedFilename,
                    upload.getSize(), EncryptionService.ALGORITHM, pbkdf2Iterations, salt, iv));
            return response(metadata);
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            Files.deleteIfExists(partial);
            Files.deleteIfExists(target);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<FileMetadataResponse> list(String email) {
        User user = authService.findByEmail(email);
        return fileRepository.findAllByUserIdOrderByUploadedAtDesc(user.getId()).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public FileMetadata ownedFile(String email, Long id) {
        User user = authService.findByEmail(email);
        return fileRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));
    }

    public Path encryptedPath(FileMetadata metadata) {
        Path path = FileUtil.safeResolve(storageDirectory, metadata.getEncryptedFilename());
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Encrypted file is missing from storage");
        }
        return path;
    }

    public Path decryptToTemporaryFile(FileMetadata metadata, String password) throws IOException {
        if (password == null || password.isEmpty() || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid decryption password");
        }
        Path encryptedPath = encryptedPath(metadata);
        Path temporaryFile = Files.createTempFile("securevault-decrypted-", ".tmp");
        try {
                Cipher cipher = encryptionService.decryptionCipher(password, metadata.getSalt(), metadata.getIv(),
                    metadata.getPbkdf2Iterations());
            try (InputStream input = new CipherInputStream(Files.newInputStream(encryptedPath), cipher);
                 OutputStream output = Files.newOutputStream(temporaryFile)) {
                input.transferTo(output);
            }
            return temporaryFile;
        } catch (IOException | GeneralSecurityException exception) {
            Files.deleteIfExists(temporaryFile);
            if (causedByAuthenticationFailure(exception)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The password is incorrect or the encrypted file has been modified");
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Unable to decrypt the file", exception);
        }
    }

    @Transactional
    public void delete(String email, Long id) throws IOException {
        FileMetadata metadata = ownedFile(email, id);
        Files.deleteIfExists(FileUtil.safeResolve(storageDirectory, metadata.getEncryptedFilename()));
        fileRepository.delete(metadata);
    }

    private boolean causedByAuthenticationFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof javax.crypto.AEADBadTagException || cause instanceof javax.crypto.BadPaddingException) {
                return true;
            }
        }
        return false;
    }

    private FileMetadataResponse response(FileMetadata metadata) {
        return new FileMetadataResponse(metadata.getId(), metadata.getOriginalFilename(), metadata.getFileSize(),
                metadata.getEncryptionAlgorithm(), metadata.getUploadedAt());
    }
}