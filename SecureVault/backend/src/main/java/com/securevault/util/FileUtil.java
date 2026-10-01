package com.securevault.util;

import java.nio.file.Path;
import java.util.UUID;

public final class FileUtil {
    private FileUtil() {
    }

    public static String safeOriginalFilename(String filename) {
        String name = filename == null ? "file" : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            name = "file";
        }
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    public static String encryptedFilename() {
        return UUID.randomUUID() + ".enc";
    }

    public static Path safeResolve(Path storageDirectory, String filename) {
        Path base = storageDirectory.toAbsolutePath().normalize();
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.startsWith(base) || !resolved.getFileName().toString().equals(filename)) {
            throw new IllegalArgumentException("Invalid stored filename");
        }
        return resolved;
    }
}